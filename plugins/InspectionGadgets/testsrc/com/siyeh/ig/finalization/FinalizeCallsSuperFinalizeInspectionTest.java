package com.siyeh.ig.finalization;

import com.IGInspectionTestCase;

public class FinalizeCallsSuperFinalizeInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/finalization/finalize_calls_super_finalize", new FinalizeCallsSuperFinalizeInspection());
    }
}