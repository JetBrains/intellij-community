package com.siyeh.ig.bugs;

import com.IGInspectionTestCase;

public class ConstantAssertWithSideEffectsInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/bugs/assert_with_side_effects",
                new AssertWithSideEffectsInspection());
    }
}