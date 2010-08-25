package com.siyeh.ig.performance;

import com.IGInspectionTestCase;

public class ManualArrayToCollectionCopyInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/performance/manual_array_to_collection_copy",
                new ManualArrayToCollectionCopyInspection());
    }
}