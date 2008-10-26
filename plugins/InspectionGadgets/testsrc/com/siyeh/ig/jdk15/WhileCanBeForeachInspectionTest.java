package com.siyeh.ig.jdk15;

import com.IGInspectionTestCase;

public class WhileCanBeForeachInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/jdk15/while_can_be_foreach", 
                new WhileCanBeForeachInspection());
    }
}
