package com.siyeh.ig.abstraction;

import com.IGInspectionTestCase;

public class DeclareCollectionAsInterfaceInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/abstraction/declare_collection_as_interface",
                new DeclareCollectionAsInterfaceInspection());
    }
}