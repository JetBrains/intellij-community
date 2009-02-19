package com.siyeh.ig.performance;

import com.IGInspectionTestCase;

public class StringConcatenationInLoopsInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/performance/string_concatenation_in_loops",
                new StringConcatenationInLoopsInspection());
    }
}