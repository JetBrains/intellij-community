package com.siyeh.ig.migration;

import com.siyeh.ig.IGInspectionTestCase;

public class MethodCanBeVariableArityMethodInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/migration/method_can_be_variable_arity_method",
                new MethodCanBeVariableArityMethodInspection());
    }
}