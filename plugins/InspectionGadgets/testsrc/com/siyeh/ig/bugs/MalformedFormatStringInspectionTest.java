package com.siyeh.ig.bugs;

import com.IGInspectionTestCase;

public class MalformedFormatStringInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/bugs/malformed_format_string",
                new MalformedFormatStringInspection());
    }
}