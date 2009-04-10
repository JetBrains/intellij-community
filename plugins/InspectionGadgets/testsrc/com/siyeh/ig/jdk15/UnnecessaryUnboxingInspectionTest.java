package com.siyeh.ig.jdk15;

import com.IGInspectionTestCase;

public class UnnecessaryUnboxingInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/jdk15/unnecessary_unboxing",
                new UnnecessaryUnboxingInspection());
    }
}