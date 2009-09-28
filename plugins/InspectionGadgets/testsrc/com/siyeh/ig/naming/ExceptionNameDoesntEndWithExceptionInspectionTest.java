package com.siyeh.ig.naming;

import com.IGInspectionTestCase;

public class ExceptionNameDoesntEndWithExceptionInspectionTest
        extends IGInspectionTestCase{

    public void test() throws Exception {
        doTest("com/siyeh/igtest/naming/exception_name_doesnt_end_with_exception",
                new ExceptionNameDoesntEndWithExceptionInspection());
    }

}