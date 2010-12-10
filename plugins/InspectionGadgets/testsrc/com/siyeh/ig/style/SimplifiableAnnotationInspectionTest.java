package com.siyeh.ig.style;

import com.IGInspectionTestCase;

public class SimplifiableAnnotationInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/style/simplifiable_annotation",
                new SimplifiableAnnotationInspection());
    }
}