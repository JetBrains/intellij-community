package com.siyeh.ig.style;

import com.IGInspectionTestCase;

public class UnnecessaryPerenthesesInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/style/unnecessary_parentheses",
                new UnnecessaryParenthesesInspection());
    }
}