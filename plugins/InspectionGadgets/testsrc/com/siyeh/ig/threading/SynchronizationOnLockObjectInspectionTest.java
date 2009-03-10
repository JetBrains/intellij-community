package com.siyeh.ig.threading;

import com.IGInspectionTestCase;

public class SynchronizationOnLockObjectInspectionTest
        extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/threading/synchronization_on_lock_object",
                new SynchronizeOnLockInspection());
    }
}