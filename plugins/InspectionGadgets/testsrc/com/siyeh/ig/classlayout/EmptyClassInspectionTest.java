package com.siyeh.ig.classlayout;

import com.siyeh.ig.IGInspectionTestCase;

public class EmptyClassInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/classlayout/emptyclass", new EmptyClassInspection());
    }
}