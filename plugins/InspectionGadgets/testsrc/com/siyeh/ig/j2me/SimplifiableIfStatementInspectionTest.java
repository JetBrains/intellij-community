package com.siyeh.ig.j2me;

import com.IGInspectionTestCase;

public class SimplifiableIfStatementInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/j2me/simplifiable_if_statement",
                new SimplifiableIfStatementInspection());
    }
}