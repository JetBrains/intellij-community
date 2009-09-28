package com.siyeh.ig.style;

import com.IGInspectionTestCase;

public class UnnecessarySemicolonInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/style/unnecessary_semicolon",
                new UnnecessarySemicolonInspection());
    }
}