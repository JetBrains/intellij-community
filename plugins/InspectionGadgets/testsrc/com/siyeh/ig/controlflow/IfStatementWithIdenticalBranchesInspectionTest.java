package com.siyeh.ig.controlflow;

import com.IGInspectionTestCase;

public class IfStatementWithIdenticalBranchesInspectionTest
        extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/controlflow/if_statement_with_identical_branches",
                new IfStatementWithIdenticalBranchesInspection());
    }
}