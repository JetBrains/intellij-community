package com.siyeh.ig.numeric;

import com.siyeh.ig.IGInspectionTestCase;

public class ImplicitNumericConversionInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/numeric/implicit_numeric_conversion",
                new ImplicitNumericConversionInspection());
    }
}