package com.siyeh.ig.style;

import com.IGInspectionTestCase;

public class UnnecessaryConstantArrayCreationExpressionInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/style/unnecessary_constant_array_creation_expression", 
                new UnnecessaryConstantArrayCreationExpressionInspection());
    }
}