package com.siyeh.ig.classlayout;

import com.IGInspectionTestCase;

public class UtilityClassWithPublicConstructorInspectionTest
        extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/classlayout/utility_class_with_public_constructor",
                new UtilityClassWithPublicConstructorInspection());
    }
}