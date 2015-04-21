package com.siyeh.ig.javadoc;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class HtmlTagCanBeJavadocTagInspectionTest extends LightInspectionTestCase {

  public void testHtmlTagCanBeJavadocTag() throws Exception {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new HtmlTagCanBeJavadocTagInspection();
  }
}