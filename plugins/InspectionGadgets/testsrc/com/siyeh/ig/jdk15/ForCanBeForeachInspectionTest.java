package com.siyeh.ig.jdk15;

import com.IGInspectionTestCase;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;

public class ForCanBeForeachInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/jdk15/foreach", new LocalInspectionToolWrapper(new ForCanBeForeachInspection()), "java 1.5");
    }
}