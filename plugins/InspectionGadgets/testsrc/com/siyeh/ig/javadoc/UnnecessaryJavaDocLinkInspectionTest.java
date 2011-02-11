package com.siyeh.ig.javadoc;

import com.IGInspectionTestCase;

public class UnnecessaryJavaDocLinkInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/style/unnecessary_javadoc_link",
                new UnnecessaryJavaDocLinkInspection());
    }
}