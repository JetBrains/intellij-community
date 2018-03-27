package com.siyeh.ig.fixes.style;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.style.StringBufferReplaceableByStringInspection;

public class StringBufferReplaceableByStringFixTest extends IGQuickFixesTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new StringBufferReplaceableByStringInspection());
    myRelativePath = "style/replace_with_string";
    myDefaultHint = InspectionGadgetsBundle.message("string.buffer.replaceable.by.string.quickfix");
  }

  public void testSimpleStringBuffer() { doTest(); }
  public void testStringBuilderAppend() { doTest(InspectionGadgetsBundle.message("string.builder.replaceable.by.string.quickfix")); }
  public void testStringBufferVariable() { doTest(); }
  public void testStringBufferVariable2() { doTest(); }
  public void testStartsWithPrimitive() { doTest(); }
  public void testPrecedence() { doTest(InspectionGadgetsBundle.message("string.builder.replaceable.by.string.quickfix")); }
  public void testPrecedence2() { doTest(InspectionGadgetsBundle.message("string.builder.replaceable.by.string.quickfix")); }
  public void testPrecedence3() { doTest(InspectionGadgetsBundle.message("string.builder.replaceable.by.string.quickfix")); }
  public void testPrecedence4() { doTest(InspectionGadgetsBundle.message("string.builder.replaceable.by.string.quickfix")); }
  public void testNonString1() { doTest(InspectionGadgetsBundle.message("string.builder.replaceable.by.string.quickfix")); }
  public void testNonString2() { doTest(InspectionGadgetsBundle.message("string.builder.replaceable.by.string.quickfix")); }
  public void testMarathon() { doTest(InspectionGadgetsBundle.message("string.builder.replaceable.by.string.quickfix")); }
  public void testArray() { doTest(InspectionGadgetsBundle.message("string.builder.replaceable.by.string.quickfix")); }
  public void testArray2() { doTest(InspectionGadgetsBundle.message("string.builder.replaceable.by.string.quickfix")); }
  public void testConstructorArgument() { doTest(InspectionGadgetsBundle.message("string.builder.replaceable.by.string.quickfix")); }
  public void testConstructorArgument2() { doTest(InspectionGadgetsBundle.message("string.builder.replaceable.by.string.quickfix")); }
  public void testNoConstructorArgument() { doTest(InspectionGadgetsBundle.message("string.builder.replaceable.by.string.quickfix")); }
  public void testCharLiteral() { doTest(InspectionGadgetsBundle.message("string.builder.replaceable.by.string.quickfix")); }
  public void testEscape() { doTest(InspectionGadgetsBundle.message("string.builder.replaceable.by.string.quickfix")); }
  public void testUnescape() { doTest(InspectionGadgetsBundle.message("string.builder.replaceable.by.string.quickfix")); }
  public void testMethodCallOnString() { doTest(InspectionGadgetsBundle.message("string.builder.replaceable.by.string.quickfix")); }
  public void testComplex1() { doTest(InspectionGadgetsBundle.message("string.builder.replaceable.by.string.quickfix")); }
  public void testComplex2() { doTest(InspectionGadgetsBundle.message("string.builder.replaceable.by.string.quickfix")); }
  public void testLinebreaks() { doTest(InspectionGadgetsBundle.message("string.builder.replaceable.by.string.quickfix")); }
  public void testSlashSlashInLiteral() { doTest(InspectionGadgetsBundle.message("string.builder.replaceable.by.string.quickfix")); }
  public void testHelperVariable() { doTest(InspectionGadgetsBundle.message("string.builder.replaceable.by.string.quickfix")); }
  public void testComment1() { doTest(); }
  public void testComment2() { doTest(InspectionGadgetsBundle.message("string.builder.replaceable.by.string.quickfix")); }
  public void testComment3() { doTest(); }

  public void testComplexSignOnNextLine() {
    final CommonCodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    settings.BINARY_OPERATION_SIGN_ON_NEXT_LINE = true;
    try {
      doTest(InspectionGadgetsBundle.message("string.builder.replaceable.by.string.quickfix"));
    }
    finally {
      settings.BINARY_OPERATION_SIGN_ON_NEXT_LINE = false;
    }
  }

}
