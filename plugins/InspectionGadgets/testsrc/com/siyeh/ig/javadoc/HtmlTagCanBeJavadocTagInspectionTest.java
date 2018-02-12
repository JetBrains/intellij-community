package com.siyeh.ig.javadoc;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class HtmlTagCanBeJavadocTagInspectionTest extends LightInspectionTestCase {

  public void testHtmlTagCanBeJavadocTag() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new HtmlTagCanBeJavadocTagInspection();
  }
}