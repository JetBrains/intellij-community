/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInspection.i18n;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;


public class I18NInspectionTest extends LightJavaCodeInsightFixtureTestCase {

  I18nInspection myTool = new I18nInspection();
  
  private void doTest() {
    myTool.setReportUnannotatedReferences(true);
    myFixture.enableInspections(myTool);
    myFixture.testHighlighting("i18n/" + getTestName(false) + ".java");
  }

  private void doTestNlsMode() {
    boolean old = myTool.setIgnoreForAllButNls(true);
    try {
      doTest();
    }
    finally {
      myTool.setIgnoreForAllButNls(old);
    }
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  public void testHardCodedStringLiteralAsParameter() { doTest(); }
  public void testReturnTypeInheritsNonNlsAnnotationFromParent() { doTest(); }
  public void testRecursiveInheritance() { doTest(); }
  public void testParameterInheritsNonNlsAnnotationFromSuper() { doTest(); }
  public void testLocalVariables() { doTest(); }
  public void testFields() { doTest(); }
  public void testInAnnotationArguments() { doTest(); }
  public void testAnonymousClassConstructorParameter() { doTest(); }
  public void testStringBufferNonNls() { doTest(); }
  public void testEnum() { doTest(); }
  public void testIgnoredLines() { doTest(); }
  public void testStringMethods() { doTest(); }

  public void testVarargNonNlsParameter() { doTest(); }
  public void testInitializerInAnonymousClass() { doTest(); }
  public void testNonNlsArray() { doTest(); }
  public void testNonNlsEquals() { doTest(); }
  public void testNonNlsTernary() { doTest(); }
  public void testParameterInNewAnonymousClass() { doTest(); }
  public void testConstructorCallOfNonNlsVariable() { doTest(); }
  public void _testConstructorChains() { doTest(); }
  public void testSwitchOnNonNlsString() { doTest(); }
  public void testNestedArrayParenthesized() { doTest(); }
  public void testNonNlsComment() {
    myTool.setNonNlsCommentPattern("MYNON-NLS");
    doTest();
  }
  public void testPropagateToInterfaceMethod() {
    doTest();
  }

  public void testNlsOnly() {
    doTestNlsMode();
  }
  
  public void testNlsOnlyTernary() {
    doTestNlsMode();
  }
  
  public void testNlsOnlyFields() {
    doTestNlsMode();
  }

  public void testNlsPackage() {
    myFixture.addFileToProject("package-info.java", "@Nls\n" +
                                                    "package foo;\n" +
                                                    "import org.jetbrains.annotations.Nls;");
    doTestNlsMode();
  }

  public void testAnnotationArgument() { doTest(); }
  public void testAssertionStmt() { doTest(); }
  public void testExceptionCtor() { doTest(); }
  public void testSpecifiedExceptionCtor() {
    boolean old = myTool.ignoreForExceptionConstructors;
    try {
      myTool.ignoreForSpecifiedExceptionConstructors = "java.io.IOException";
      myTool.ignoreForExceptionConstructors = false;
      doTest();
    }
    finally {
      myTool.ignoreForSpecifiedExceptionConstructors = "";
      myTool.ignoreForExceptionConstructors = old;
    }
  }

  public void testEnumConstantIgnored() {
    boolean oldState = myTool.setIgnoreForEnumConstants(true);
    try {
      doTest();
    }
    finally {
      myTool.setIgnoreForEnumConstants(oldState);
    }
  }
  
  public void testNlsTypeUse() { doTestNlsMode(); }

  public void testNonNlsIndirect() { doTest(); }

  public void testNlsIndirect() { doTestNlsMode(); }
  
  public void testNonNlsMeta() { doTest(); }
  
  public void testNlsMeta() { doTestNlsMode(); }
  
  public void testUseConstant() { doTest(); }

  public void testNonNlsOnContainer() { doTest(); }
  
  public void testUseConstantNls() { doTestNlsMode(); }
  
  public void testHtmlEntitiesNlsMode() {
    doTestNlsMode();
  }

  public void testArrayInitializerInNlsMode() { doTestNlsMode(); }

  public void testSwitchInNlsMode() { doTestNlsMode(); }

  public void testReturnCallWithArgs() { doTest(); }
  
  public void testConverterMethods() { doTestNlsMode(); }
  
  public void testPassthroughMethods() { doTestNlsMode(); }
  
  public void testNlsSafeStringBuilder() { doTestNlsMode(); }
  
  public void testUnannotatedReferencesQualified() { doTestNlsMode(); }
  
  public void testCharSequenceMethods() { doTestNlsMode(); }
  
  public void testRefsNonNlsMode() { doTest(); }
  
  public void testRefsMetaAnnotation() { doTest(); }
  
  public void testStringBuilderAppend() { doTest(); }
  
  public void testComputeIfAbsent() { doTest(); }
  
  public void testNonNlsFieldInitializer() { doTest(); }

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("java-i18n") + "/testData/inspections";
  }
}
