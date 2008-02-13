package com.siyeh.ig.style;

import com.IGInspectionTestCase;

public class UnnecessaryThisInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/style/unnecessary_this", new UnnecessaryThisInspection());
    }
}