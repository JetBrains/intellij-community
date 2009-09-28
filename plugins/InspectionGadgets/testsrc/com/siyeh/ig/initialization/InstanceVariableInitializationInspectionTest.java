package com.siyeh.ig.initialization;

import com.IGInspectionTestCase;

public class InstanceVariableInitializationInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/initialization/field",
                new InstanceVariableInitializationInspection());
    }
}