package com.siyeh.ig.style;

import com.siyeh.ig.IGInspectionTestCase;

public class UnqualifiedInnerClassAccessInspectionTest
        extends IGInspectionTestCase {

    public void test() throws Exception {
      doTest("com/siyeh/igtest/style/unqualified_inner_class_access",
                new UnqualifiedInnerClassAccessInspection());
    }
}