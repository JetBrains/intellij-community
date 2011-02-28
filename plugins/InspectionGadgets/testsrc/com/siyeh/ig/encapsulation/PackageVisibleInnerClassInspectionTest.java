package com.siyeh.ig.encapsulation;

import com.siyeh.ig.IGInspectionTestCase;

public class PackageVisibleInnerClassInspectionTest extends
        IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/encapsulation/package_visible_inner_class",
                new PackageVisibleInnerClassInspection());
    }
}
