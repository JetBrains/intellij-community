package com.siyeh.ig.controlflow;

import com.IGInspectionTestCase;

public class ConfusingElseInspectionTest
        extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/controlflow/confusing_else",
                new ConfusingElseInspection());
    }
}