package com.siyeh.ig.style;

import com.IGInspectionTestCase;

public class UnnecessaryAnnotationParenthesesInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/style/unnecessary_annotation_parentheses",
                new UnnecessaryAnnotationParenthesesInspection());
    }
}