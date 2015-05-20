package com.siyeh.ig.fixes.javadoc;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.javadoc.HtmlTagCanBeJavadocTagInspection;

public class HtmlTagCanBeJavadocTagFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new HtmlTagCanBeJavadocTagInspection());
    myRelativePath = "javadoc/html_tag_can_be_javadoc_tag";
    myDefaultHint = InspectionGadgetsBundle.message("html.tag.can.be.javadoc.tag.quickfix");
  }

  public void testBraces() { doTest(); }
  public void testBraces2() { doTest(); }
  public void testSecond() { doTest(); }
  public void testMultiline() { doTest(); }
}