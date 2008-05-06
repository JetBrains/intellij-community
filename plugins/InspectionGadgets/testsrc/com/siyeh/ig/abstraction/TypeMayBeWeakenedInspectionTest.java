package com.siyeh.ig.abstraction;

import com.IGInspectionTestCase;

public class TypeMayBeWeakenedInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/abstraction/weaken_type",
                new TypeMayBeWeakenedInspection());
    }
}