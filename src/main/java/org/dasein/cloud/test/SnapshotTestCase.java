/**
 * Copyright (C) 2009-2012 enStratus Networks Inc
 *
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ====================================================================
 */

package org.dasein.cloud.test;

import java.io.IOException;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import org.dasein.cloud.CloudException;
import org.dasein.cloud.CloudProvider;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.compute.ComputeServices;
import org.dasein.cloud.compute.Snapshot;
import org.dasein.cloud.compute.SnapshotCreateOptions;
import org.dasein.cloud.compute.SnapshotState;
import org.dasein.cloud.compute.SnapshotSupport;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.compute.VirtualMachineSupport;
import org.dasein.cloud.compute.VmState;
import org.dasein.cloud.compute.Volume;
import org.dasein.cloud.compute.VolumeCreateOptions;
import org.dasein.cloud.compute.VolumeProduct;
import org.dasein.cloud.compute.VolumeState;
import org.dasein.cloud.compute.VolumeSupport;
import org.dasein.cloud.dc.Region;
import org.dasein.cloud.util.APITrace;
import org.dasein.util.CalendarWrapper;
import org.dasein.util.uom.storage.Gigabyte;
import org.dasein.util.uom.storage.Storage;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.annotation.Nullable;

@SuppressWarnings("JUnit4AnnotatedMethodInJUnit3TestCase")
public class SnapshotTestCase extends BaseTestCase {
    static public final String T_CREATE_SNAPSHOT            = "testCreateSnapshot";
    static public final String T_GET_SNAPSHOT               = "testGetSnapshot";
    static public final String T_REMOVE_ALL_SHARES          = "testRemoveAllShares";
    static public final String T_REMOVE_PUBLIC_SHARE        = "testRemovePublicSnapshotShare";
    static public final String T_REMOVE_SHARE               = "testRemoveTargetedSnapshotShare";
    static public final String T_REMOVE_SNAPSHOT            = "testRemoveSnapshot";
    static public final String T_SHARE_SNAPSHOT             = "testShareSnapshotWithTarget";
    static public final String T_SHARE_SNAPSHOT_WITH_PUBLIC = "testShareSnapshotWithPublic";
    static public final String T_SNAPSHOT_CONTENT           = "testSnapshotContent";

    static private final String[] NEEDS_VOLUMES = { T_SNAPSHOT_CONTENT, T_GET_SNAPSHOT, T_CREATE_SNAPSHOT, T_REMOVE_SNAPSHOT, T_SHARE_SNAPSHOT_WITH_PUBLIC, T_REMOVE_PUBLIC_SHARE, T_SHARE_SNAPSHOT, T_REMOVE_SHARE, T_REMOVE_ALL_SHARES };

    static private VirtualMachine testVm        = null;

    private CloudProvider  provider         = null;
    private String         snapshotToDelete = null;
    private Snapshot       testSnapshot     = null;
    private String         testVolume       = null;

    public SnapshotTestCase(String name) { super(name); }

    private @Nullable Snapshot createTestSnapshot() throws CloudException, InternalException {
        if( getSnapshotSupport().identifyAttachmentRequirement().equals(Requirement.REQUIRED) ) {
            try {
                Volume v = getVolumeSupport().getVolume(testVolume);

                if( v != null ) {
                    if( v.getProviderVirtualMachineId() == null ) {
                        if( testVm != null ) {
                            testVm = provider.getComputeServices().getVirtualMachineSupport().getVirtualMachine(testVm.getProviderVirtualMachineId());
                            if( testVm != null && testVm.getCurrentState().equals(VmState.TERMINATED) ) {
                                testVm = null;
                            }
                        }
                        if( testVm == null ) {
                            //noinspection ConstantConditions
                            testVm = findTestVirtualMachine(provider, provider.getComputeServices().getVirtualMachineSupport(), false, true);
                        }
                        try {
                            for( String deviceId : getVolumeSupport().listPossibleDeviceIds(testVm.getPlatform()) ) {
                                try {
                                    getVolumeSupport().attach(testVolume, testVm.getProviderVirtualMachineId(), deviceId);
                                    break;
                                }
                                catch( Throwable ignore ) {
                                    // ignore
                                }
                            }
                        }
                        catch( Throwable ignore ) {
                            // ignore
                        }
                    }
                }
            }
            catch( Throwable ignore ) {
                // ignore
            }
        }
        Snapshot snapshot = getSnapshotSupport().snapshot(testVolume, getName() + "-" + (System.currentTimeMillis()%10000), getName() + " from " + testVolume);

        if( snapshot == null ) {
            Assert.fail("Need data changes in order to create test snapshot");
        }
        snapshotToDelete = snapshot.getProviderSnapshotId();

        long timeout = System.currentTimeMillis() + getStateChangeWindow();

        while( timeout > System.currentTimeMillis() ) {
            if( snapshot == null || !SnapshotState.PENDING.equals(snapshot.getCurrentState()) ) {
                break;
            }
            try { Thread.sleep(15000L); }
            catch( InterruptedException ignore ) { }
            try { snapshot = getSnapshotSupport().getSnapshot(snapshotToDelete); }
            catch( Throwable ignore ) { }
        }
        return snapshot;
    }

    /*
    private void createTestVolume() throws CloudException, InternalException {
        volumeUse++;
        if( testVm == null ) {
            //noinspection ConstantConditions
            testVm = findTestVirtualMachine(provider, provider.getComputeServices().getVirtualMachineSupport(), false, true);
        }
        VolumeCreateOptions options;

        String name = "snap" + getName() + "-" + (System.currentTimeMillis()%10000);
        VolumeProduct product = null;

        if( getVolumeSupport().getVolumeProductRequirement().equals(Requirement.REQUIRED) || getVolumeSupport().isVolumeSizeDeterminedByProduct() ) {
            for( VolumeProduct prd : getVolumeSupport().listVolumeProducts() ) {
                if( product == null ) {
                    product = prd;
                }
                else {
                    Float thisCost = prd.getMonthlyGigabyteCost();
                    Float currentCost = product.getMonthlyGigabyteCost();

                    if( currentCost == null || currentCost < 0.001f ) {
                        Storage<Gigabyte> thisSize = prd.getVolumeSize();
                        Storage<Gigabyte> currentSize = product.getVolumeSize();

                        if( currentSize == null || (thisSize != null && thisSize.intValue() < currentSize.intValue()) ) {
                            product = prd;
                        }
                    }
                    else if( thisCost != null && thisCost > 0.0f && thisCost < currentCost ) {
                        product = prd;
                    }
                }
            }
        }
        if( product != null ) {
            Storage<Gigabyte> size = product.getVolumeSize();

            if( size == null || size.longValue() < 1L ) {
                size = getVolumeSupport().getMinimumVolumeSize();
            }
            options = VolumeCreateOptions.getInstance(product.getProviderProductId(), size, name, name, 0);
        }
        else {
            options = VolumeCreateOptions.getInstance(getVolumeSupport().getMinimumVolumeSize(), name, name);
        }
        testVolume = getVolumeSupport().createVolume(options);

        long timeout = System.currentTimeMillis() + getStateChangeWindow();
        Volume v = getVolumeSupport().getVolume(testVolume);

        while( timeout > System.currentTimeMillis() ) {
            Assert.assertNotNull("Test volume disappeared", v);
            if( !VolumeState.PENDING.equals(v.getCurrentState()) ) {
                break;
            }
            try { Thread.sleep(15000L); }
            catch( InterruptedException ignore ) { }
            try { v = getVolumeSupport().getVolume(testVolume); }
            catch( Throwable ignore ) { }
        }
        if( v != null && v.getProviderVirtualMachineId() == null && getSnapshotSupport().identifyAttachmentRequirement().equals(Requirement.REQUIRED) && testVm != null ) {
            try {
                for( String deviceId : getVolumeSupport().listPossibleDeviceIds(testVm.getPlatform()) ) {
                    try {
                        getVolumeSupport().attach(testVolume, testVm.getProviderVirtualMachineId(), deviceId);
                        break;
                    }
                    catch( Throwable ignore ) {
                        // ignore
                    }
                }
            }
            catch( Throwable ignore ) {
                // ignore
            }
        }
    }
    */

    private SnapshotSupport getSnapshotSupport() {
        if( provider == null ) {
            Assert.fail("No provider configuration set up");
        }
        ComputeServices services = provider.getComputeServices();

        Assert.assertNotNull("No compute services exist in " + provider.getCloudName(), services);

        SnapshotSupport support = services.getSnapshotSupport();

        Assert.assertNotNull("No snapshot support exist in " + provider.getCloudName(), support);
        return support;
    }

    @Override
    public int getVmReuseCount() {
        return NEEDS_VOLUMES.length;
    }

    @Override
    public int getVolumeReuseCount() {
        return getVmReuseCount();
    }

    private VolumeSupport getVolumeSupport() {
        if( provider == null ) {
            Assert.fail("No provider configuration set up");
        }
        ComputeServices services = provider.getComputeServices();

        Assert.assertNotNull("No compute services exist in " + provider.getCloudName(), services);

        VolumeSupport support = services.getVolumeSupport();

        Assert.assertNotNull("No volume support exist in " + provider.getCloudName(), support);
        return support;
    }

    @Before
    @Override
    public void setUp() throws CloudException, InternalException, InstantiationException, IllegalAccessException, IOException {
        begin();
        provider = getProvider();
        provider.connect(getTestContext());
        for( String test : NEEDS_VOLUMES ) {
            if( test.equals(getName()) ) {
                testVolume = findTestVolume(provider, getVolumeSupport(), false, true).getProviderVolumeId();

                @SuppressWarnings("ConstantConditions") VirtualMachineSupport vmSupport = provider.getComputeServices().getVirtualMachineSupport();

                if( vmSupport != null ) {
                    // must provision VM even if we don't intend to attach it
                    testVm = findTestVirtualMachine(provider, vmSupport, false, true);
                    if( getSnapshotSupport().identifyAttachmentRequirement().equals(Requirement.REQUIRED) ) {
                        try {
                            Volume v = getVolumeSupport().getVolume(testVolume);

                            if( v == null ) {
                                throw new CloudException("Test volume disappeared");
                            }
                            String vmId = v.getProviderVirtualMachineId();

                            if( vmId == null || !vmId.equals(testVm.getProviderVirtualMachineId()) ) {
                                if( vmId != null ) {
                                    VirtualMachine vm = vmSupport.getVirtualMachine(vmId);

                                    if( vm == null || VmState.TERMINATED.equals(vm.getCurrentState()) ) {
                                        try {
                                            getVolumeSupport().detach(testVolume,  true);
                                        }
                                        catch( Throwable ignore ) {
                                            // ignore
                                        }
                                    }
                                }
                                for( String deviceId : getVolumeSupport().listPossibleDeviceIds(testVm.getPlatform()) ) {
                                    try {
                                        getVolumeSupport().attach(testVolume, testVm.getProviderVirtualMachineId(), deviceId);
                                        break;
                                    }
                                    catch( Throwable ignore ) {
                                        // ignore
                                    }
                                }
                            }
                        }
                        catch( Throwable ignore ) {
                            // ignore
                        }
                    }
                }
            }
        }
        if( getName().equals(T_SNAPSHOT_CONTENT) || getName().equals(T_GET_SNAPSHOT) ) {
            for( Snapshot s : getSnapshotSupport().listSnapshots() ) {
                if( testSnapshot == null || SnapshotState.AVAILABLE.equals(s.getCurrentState()) ) {
                    testSnapshot = s;
                }
            }
            if( testSnapshot == null ) {
                testSnapshot = createTestSnapshot();
            }
            Assert.assertNotNull("Unable to identify a test snapshot for this test", testSnapshot);
        }
        else if( getName().equals(T_SHARE_SNAPSHOT_WITH_PUBLIC) || getName().equals(T_REMOVE_PUBLIC_SHARE) || getName().equals(T_SHARE_SNAPSHOT) || getName().equals(T_REMOVE_SHARE) || getName().equals(T_REMOVE_ALL_SHARES) ) {
            testSnapshot = createTestSnapshot();
            if( getSnapshotSupport().supportsSnapshotSharingWithPublic() && getName().equals(T_REMOVE_PUBLIC_SHARE) && testSnapshot != null ) {
                getSnapshotSupport().addPublicShare(testSnapshot.getProviderSnapshotId());
                Assert.assertTrue("Did not set up sharing for share removal test", getSnapshotSupport().isPublic(testSnapshot.getProviderSnapshotId()));
            }
            else if( getSnapshotSupport().supportsSnapshotSharing() && (getName().equals(T_REMOVE_SHARE) || getName().equals(T_REMOVE_ALL_SHARES)) && testSnapshot != null ) {
                String sharedAccount = System.getProperty("test.shareAccount");

                if( sharedAccount != null ) {
                    getSnapshotSupport().addSnapshotShare(testSnapshot.getProviderSnapshotId(), sharedAccount);
                    boolean shared = false;

                    for( String share : getSnapshotSupport().listShares(testSnapshot.getProviderSnapshotId()) ) {
                        if( sharedAccount.equals(share) ) {
                            shared = true;
                            break;
                        }
                    }
                    Assert.assertTrue("The account is not shown as being shared with " + sharedAccount, shared);
                }
                else {
                    out("Warning: cannot execute share removal test due to lack of share configuration (test.shareAccount)");
                }
            }
        }
        else if( getName().equals(T_REMOVE_SNAPSHOT) ) {
            testSnapshot = createTestSnapshot();
        }
    }

    @After
    @Override
    public void tearDown() {
        try {
            if( snapshotToDelete != null ) {
                try {
                    String id = null;

                    try {
                        Snapshot snapshot = getSnapshotSupport().getSnapshot(snapshotToDelete);

                        if( snapshot != null ) {
                            id = snapshot.getProviderSnapshotId();
                        }
                    }
                    catch( Throwable t ) {
                        id = snapshotToDelete;
                    }
                    if( id != null ) {
                        long timeout = System.currentTimeMillis() + getStateChangeWindow();

                        while( timeout > System.currentTimeMillis() ) {
                            try {
                                Snapshot s = getSnapshotSupport().getSnapshot(id);

                                if( s == null || !SnapshotState.PENDING.equals(s.getCurrentState()) ) {
                                    break;
                                }
                            }
                            catch( Throwable ignore ) {
                                // ignore
                            }
                            try { Thread.sleep(20000L); }
                            catch( InterruptedException ignore ) { }
                        }
                        try {
                            getSnapshotSupport().remove(id);
                        }
                        catch( Throwable e ) {
                            out("Warning: Error cleaning up test snapshot: " + e.getMessage());
                        }
                    }
                }
                finally {
                    snapshotToDelete = null;
                    testSnapshot = null;
                }
            }
            cleanUp(provider);
            APITrace.report(getName());
            APITrace.reset();
            try {
                if( provider != null ) {
                    provider.close();
                }
            }
            catch( Throwable ignore ) {
                // ignore
            }
        }
        finally {
            end();
        }
    }

    @Test
    public void testMetaData() throws CloudException, InternalException {
        SnapshotSupport support = getSnapshotSupport();

        String term = support.getProviderTermForSnapshot(Locale.getDefault());
        boolean subscribed = support.isSubscribed();

        out("Term for snapshot: " + term);
        out("Subscribed:        " + subscribed);
        out("Needs attachment:  " + support.identifyAttachmentRequirement());
        out("Snapshot making:   " + support.supportsSnapshotCreation());
        out("Sharing:           " + support.supportsSnapshotSharing());
        out("Public sharing:    " + support.supportsSnapshotSharingWithPublic());

        if( !subscribed ) {
            out("WARNING: Cannot assess compliance with snapshot requirements due to a lack of subscription with this account/region");
        }
        Assert.assertNotNull("Provider term for snapshot may not be null", term);
    }

    @Test
    public void testListSnapshots() throws CloudException, InternalException {
        Iterable<Snapshot> snapshots = getSnapshotSupport().listSnapshots();
        int count = 0;

        Assert.assertNotNull("listSnapshots() must return a non-null list of snapshots (may be empty)", snapshots);
        try {
            for( Snapshot s : snapshots ) {
                count++;
                out("Snapshot: " + s);
            }
            if( count < 1 ) {
                out("Warning: No snapshots were returned, difficult to assess success of this call");
            }
        }
        catch( Throwable ignore ) {
            // not part of test
        }
    }

    @Test
    public void testListSnapshotStatus() throws CloudException, InternalException {
        Iterable<ResourceStatus> snapshots = getSnapshotSupport().listSnapshotStatus();
        int count = 0;

        Assert.assertNotNull("Snapshot status list must return a non-null (may be empty)", snapshots);
        try {
            for( ResourceStatus s : snapshots ) {
                count++;
                out("Snapshot status: " + s);
            }
            if( count < 1 ) {
                out("WARNING: No snapshot status items were returned, difficult to assess success of this call");
            }
        }
        catch( Throwable ignore ) {
            // not part of test
        }
    }

    @Test
    public void testSearchSnapshots() throws CloudException, InternalException {
        Iterable<Snapshot> snapshots = getSnapshotSupport().searchSnapshots(null, "tools");
        int count = 0;

        Assert.assertNotNull("Snapshot searches must return a non-null list of snapshots (may be empty)", snapshots);
        try {
            for( Snapshot s : snapshots ) {
                count++;
                out("Snapshot: " + s);
            }
            if( count < 1 ) {
                out("Warning: No snapshots were returned, difficult to assess success of this call");
            }
        }
        catch( Throwable ignore ) {
            // not part of test
        }
    }

    @Test
    public void testGetSnapshot() throws CloudException, InternalException {
        Snapshot snapshot = getSnapshotSupport().getSnapshot(testSnapshot.getProviderSnapshotId());

        out("Snapshot: " + snapshot);
        Assert.assertNotNull("Test snapshot was not found", snapshot);
    }

    @Test
    public void testGetBogusSnapshot() throws CloudException, InternalException {
        String id = "snap-" + UUID.randomUUID().toString().substring(0,9);
        Snapshot snapshot = getSnapshotSupport().getSnapshot(id);

        out("Bogus snapshot [" + id + "]: " + snapshot);
        Assert.assertNull("A snapshot was found for the bogus ID " + id, snapshot);
    }

    @Test
    public void testSnapshotContent() throws CloudException, InternalException {
        Snapshot snapshot = getSnapshotSupport().getSnapshot(testSnapshot.getProviderSnapshotId());

        Assert.assertNotNull("Test snapshot is null for test", snapshot);
        Iterable<String> shares = getSnapshotSupport().listShares(snapshot.getProviderSnapshotId());

        out("ID:            " + snapshot.getProviderSnapshotId());
        out("State:         " + snapshot.getCurrentState());
        out("Name:          " + snapshot.getName());
        out("Owner:         " + snapshot.getOwner());
        out("Region:        " + snapshot.getRegionId());
        out("Public:        " + getSnapshotSupport().isPublic(snapshot.getProviderSnapshotId()));
        out("Volume:        " + snapshot.getVolumeId());
        out("Progress:      " + snapshot.getProgress());
        out("Size:          " + snapshot.getSizeInGb() + " GB");
        out("Timestamp:     " + (new Date(snapshot.getSnapshotTimestamp())));
        out("Shares:        " + shares);
        out("Description:   " + snapshot.getDescription());

        Assert.assertNotNull("Snapshot ID must not be null", snapshot.getProviderSnapshotId());
        Assert.assertNotNull("Snapshot name must not be null", snapshot.getName());
        Assert.assertNotNull("Snapshot description must not be null", snapshot.getDescription());
        //noinspection ConstantConditions
        Assert.assertEquals("Snapshot region does not match: " + provider.getContext().getRegionId() + " vs " + snapshot.getRegionId(), provider.getContext().getRegionId(), snapshot.getRegionId());
        Assert.assertTrue("Size must be a positive number", snapshot.getSizeInGb() > 0);
        if( snapshot.getSnapshotTimestamp() < 1L ) {
            out("Warning: Snapshot timestamp is a useless value");
        }
    }

    @Test
    public void testCreateSnapshot() throws CloudException, InternalException {
        testSnapshot = getSnapshotSupport().snapshot(testVolume, getName() + "-" + (System.currentTimeMillis()%10000), getName() + " from " + testVolume);

        out("Created: " + testSnapshot);
        if( testSnapshot != null ) {
            snapshotToDelete = testSnapshot.getProviderSnapshotId();
        }
        testSnapshot = getSnapshotSupport().getSnapshot(snapshotToDelete);
        Assert.assertNotNull("Got ID " + snapshotToDelete + ", but found no snapshot", testSnapshot);
    }

    @Test
    public void testShareSnapshotWithTarget() throws CloudException, InternalException {
        String sharedAccount = System.getProperty("test.shareAccount");

        if( getSnapshotSupport().supportsSnapshotSharing() ) {
            if( sharedAccount == null ) {
                out("Warning: Cannot run snapshot sharing tests because no share account has been configured (test.shareAccount)");
            }
            else {
                getSnapshotSupport().addSnapshotShare(testSnapshot.getProviderSnapshotId(), sharedAccount);

                boolean shared = false;

                for( String share : getSnapshotSupport().listShares(testSnapshot.getProviderSnapshotId()) ) {
                    out("Share: " + share);
                    if( sharedAccount.equals(share) ) {
                        shared = true;
                    }
                }
                Assert.assertTrue("The account is not shown as being shared with " + sharedAccount, shared);
            }
        }
        else {
            if( sharedAccount == null ) {
                sharedAccount = UUID.randomUUID().toString();
            }
            try {
                getSnapshotSupport().addSnapshotShare(testSnapshot.getProviderSnapshotId(), sharedAccount);
                Assert.fail("Call to share snapshot succeeded even though sharing is not supported in this cloud");
            }
            catch( OperationNotSupportedException e ) {
                out("Error attempting to share snapshot in unsupported cloud (OK)");
            }
            catch( CloudException e ) {
                Assert.fail("Invalid exception CloudException thrown while attempting share in unsupported cloud");
            }
            catch( InternalException e ) {
                Assert.fail("Invalid exception InternalException thrown while attempting share in unsupported cloud");
            }
        }
    }

    @Test
    public void testRemoveTargetedSnapshotShare() throws CloudException, InternalException {
        String sharedAccount = System.getProperty("test.shareAccount");

        if( getSnapshotSupport().supportsSnapshotSharing() ) {
            if( sharedAccount == null ) {
                out("Warning: test skipped due to lack of configuration");
            }
            else {
                getSnapshotSupport().removeSnapshotShare(testSnapshot.getProviderSnapshotId(), sharedAccount);

                boolean shared = false;

                for( String share : getSnapshotSupport().listShares(testSnapshot.getProviderSnapshotId()) ) {
                    out("Share: " + share);
                    if( sharedAccount.equals(share) ) {
                        shared = true;
                    }
                }
                Assert.assertFalse("The account is still shown as being shared with " + sharedAccount, shared);
            }
        }
        else {
            out("Test ignored (" + T_REMOVE_SHARE + " was sufficient)");
        }
    }

    @Test
    public void testRemoveAllShares() throws CloudException, InternalException {
        String sharedAccount = getTestShareAccount();

        if( getSnapshotSupport().supportsSnapshotSharing() ) {
            if( sharedAccount == null ) {
                out("Warning: test skipped due to lack of configuration");
            }
            else {
                Iterable<String> shares = getSnapshotSupport().listShares(testSnapshot.getProviderSnapshotId());

                out("Before: " + shares);
                getSnapshotSupport().removeAllSnapshotShares(testSnapshot.getProviderSnapshotId());
                shares = getSnapshotSupport().listShares(testSnapshot.getProviderSnapshotId());
                out("After: " + shares);
                Assert.assertFalse("There are still active shares " + sharedAccount, shares.iterator().hasNext());
            }
        }
        else {
            out("Test ignored (OK)");
        }
    }

    @Test
    public void testShareSnapshotWithPublic() throws CloudException, InternalException {
        if( getSnapshotSupport().supportsSnapshotSharingWithPublic() ) {
            Assert.assertFalse("Snapshot is already public", getSnapshotSupport().isPublic(testSnapshot.getProviderSnapshotId()));
            getSnapshotSupport().addPublicShare(testSnapshot.getProviderSnapshotId());
            boolean p = getSnapshotSupport().isPublic(testSnapshot.getProviderSnapshotId());

            out("Public: " + p);
            Assert.assertTrue("Snapshot is not being described as public", p);
        }
        else {
            try {
                getSnapshotSupport().addPublicShare(testSnapshot.getProviderSnapshotId());
                Assert.fail("Call to share snapshot publicly succeeded even though sharing is not supported in this cloud");
            }
            catch( OperationNotSupportedException e ) {
                out("Error attempting to publicly share snapshot in unsupported cloud (OK)");
            }
            catch( CloudException e ) {
                Assert.fail("Invalid exception CloudException thrown while attempting public share in unsupported cloud");
            }
            catch( InternalException e ) {
                Assert.fail("Invalid exception InternalException thrown while attempting public share in unsupported cloud");
            }
        }
    }

    @Test
    public void testRemovePublicSnapshotShare() throws CloudException, InternalException {
        if( getSnapshotSupport().supportsSnapshotSharingWithPublic() ) {
            Assert.assertTrue("Snapshot is not public", getSnapshotSupport().isPublic(testSnapshot.getProviderSnapshotId()));
            getSnapshotSupport().removePublicShare(testSnapshot.getProviderSnapshotId());
            boolean p = getSnapshotSupport().isPublic(testSnapshot.getProviderSnapshotId());

            out("Public: " + p);
            Assert.assertFalse("Snapshot is still public after killing share", p);
        }
        else {
            out("Test ignored (" + T_SHARE_SNAPSHOT_WITH_PUBLIC + " was sufficient)");
        }
    }

    @Test
    public void testRemoveSnapshot() throws CloudException, InternalException {
        String id = testSnapshot.getProviderSnapshotId();

        Snapshot snapshot = getSnapshotSupport().getSnapshot(id);

        Assert.assertNotNull("No snapshot on which to test removal", snapshot);

        getSnapshotSupport().remove(id);
        out("Removed: " + id);
        snapshot = getSnapshotSupport().getSnapshot(id);
        Assert.assertTrue("Snapshot still exists", snapshot == null || SnapshotState.DELETED.equals(snapshot.getCurrentState()));
    }

    @Test
    public void testCopySnapshot() throws CloudException, InternalException {
        if( !getSnapshotSupport().supportsSnapshotCopying() ) {
            try {
                String id = getSnapshotSupport().createSnapshot(SnapshotCreateOptions.getInstanceForCopy("nonsense", "nonsense", "nonsense", "nonsense"));

                Assert.fail("No error occurred copying nonsense");
            }
            catch( OperationNotSupportedException e ) {
                out("Snapshot copy unsupported (OK)");
            }
            catch( CloudException e ) {
                Assert.fail("Should have incurred an OperationNotSupportedException");
            }
        }
        else {
            Snapshot sourceSnapshot = null;

            for( Region r : provider.getDataCenterServices().listRegions() ) {
                //noinspection ConstantConditions
                if( !r.getProviderRegionId().equals(provider.getContext().getRegionId()) ) {
                    for( Snapshot snapshot : provider.getComputeServices().getSnapshotSupport().listSnapshots() ) {
                        if( snapshot.getCurrentState().equals(SnapshotState.AVAILABLE) ) {
                            sourceSnapshot = snapshot;
                            break;
                        }
                    }
                }
            }
            if( sourceSnapshot == null ) {
                out("WARNING: Unable to identify a source snapshot for test copy");
            }
            else {
                String id = getSnapshotSupport().createSnapshot(SnapshotCreateOptions.getInstanceForCopy(sourceSnapshot.getRegionId(), sourceSnapshot.getProviderSnapshotId(), "DSN Copy of " + sourceSnapshot.getName(), "Dasein Cloud Unit test copy of " + sourceSnapshot.getName()));

                out("Copy: " + id);
                Assert.assertNotNull("Copy must result in the creation of a new snapshot", id);
                snapshotToDelete = id;
            }
        }
    }
}
