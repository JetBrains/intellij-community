package com.siyeh.ig.javadoc;

import com.siyeh.ig.IGInspectionTestCase;

public class HtmlTagCanBeJavadocTagInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/javadoc/html_tag_can_be_javadoc_tag", new HtmlTagCanBeJavadocTagInspection());
  }
}