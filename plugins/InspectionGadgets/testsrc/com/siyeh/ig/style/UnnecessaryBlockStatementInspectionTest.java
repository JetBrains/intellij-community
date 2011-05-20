package com.siyeh.ig.style;

import com.siyeh.ig.IGInspectionTestCase;

public class UnnecessaryBlockStatementInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/style/unnecessary_block_statement",
                new UnnecessaryBlockStatementInspection());
    }
}
