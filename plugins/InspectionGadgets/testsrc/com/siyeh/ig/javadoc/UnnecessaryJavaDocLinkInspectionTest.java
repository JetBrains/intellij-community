package com.siyeh.ig.javadoc;

import com.siyeh.ig.IGInspectionTestCase;

public class UnnecessaryJavaDocLinkInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/javadoc/unnecessary_javadoc_link", new UnnecessaryJavaDocLinkInspection());
  }
}