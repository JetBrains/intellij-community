package com.siyeh.ig.style;

import com.siyeh.ig.IGInspectionTestCase;

public class UnnecessaryParenthesesInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        final UnnecessaryParenthesesInspection inspection =
                new UnnecessaryParenthesesInspection();
        inspection.ignoreParenthesesOnConditionals = true;
        doTest("com/siyeh/igtest/style/unnecessary_parentheses",
                inspection);
    }
}