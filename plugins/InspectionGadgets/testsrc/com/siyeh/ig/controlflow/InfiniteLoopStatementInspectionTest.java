package com.siyeh.ig.controlflow;

import com.IGInspectionTestCase;

public class InfiniteLoopStatementInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/controlflow/infinite_loop_statement",
                new InfiniteLoopStatementInspection());
    }
}