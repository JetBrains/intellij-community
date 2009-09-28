package com.siyeh.ig.threading;

import com.IGInspectionTestCase;

public class WhileLoopSpinsOnFieldInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/threading/spins",
                new WhileLoopSpinsOnFieldInspection());
    }
}