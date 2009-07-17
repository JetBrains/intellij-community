package com.siyeh.ig.bugs;

import com.IGInspectionTestCase;

public class ClassNewInstanceInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/bugs/class_new_instance",
                new ClassNewInstanceInspection());
    }
}