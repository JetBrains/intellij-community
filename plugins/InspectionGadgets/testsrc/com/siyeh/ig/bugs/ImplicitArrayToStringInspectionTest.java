package com.siyeh.ig.bugs;

import com.IGInspectionTestCase;

public class ImplicitArrayToStringInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/bugs/implicit_array_to_string",
                new ImplicitArrayToStringInspection());
    }
}