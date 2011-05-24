package com.siyeh.ig.numeric;

import com.siyeh.ig.IGInspectionTestCase;

public class UnnecessaryExplicitNumericCastInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/numeric/unnecessary_explicit_numeric_cast",
                new UnnecessaryExplicitNumericCastInspection ());
    }
}