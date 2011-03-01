package com.siyeh.ig.abstraction;

import com.siyeh.ig.IGInspectionTestCase;

public class TypeMayBeWeakenedInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        final TypeMayBeWeakenedInspection inspection =
                new TypeMayBeWeakenedInspection();
        inspection.doNotWeakenToJavaLangObject = false;
        inspection.onlyWeakentoInterface = false;
        doTest("com/siyeh/igtest/abstraction/weaken_type",
                inspection);
    }
}