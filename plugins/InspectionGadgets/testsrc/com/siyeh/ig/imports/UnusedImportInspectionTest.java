package com.siyeh.ig.imports;

import com.siyeh.ig.IGInspectionTestCase;

public class UnusedImportInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/imports/unused",
                new UnusedImportInspection());
    }
}