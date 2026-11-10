// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.i18n;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class I18NInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(PluginPathManager.getPluginHomePath("java-i18n") + "/testData/inspections");
  }

  I18nInspection myTool = new I18nInspection();
  
  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myTool.setReportUnannotatedReferences(true);
    myFixture.enableInspections(myTool);
    myFixture.testHighlighting("i18n/" + myFixture.getTestName(false) + ".java");
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

  @Test
  public void testHardCodedStringLiteralAsParameter() { doTest(); }
  @Test
  public void testReturnTypeInheritsNonNlsAnnotationFromParent() { doTest(); }
  @Test
  public void testRecursiveInheritance() { doTest(); }
  @Test
  public void testParameterInheritsNonNlsAnnotationFromSuper() { doTest(); }
  @Test
  public void testLocalVariables() { doTest(); }
  @Test
  public void testFields() { doTest(); }
  @Test
  public void testInAnnotationArguments() { doTest(); }
  @Test
  public void testAnonymousClassConstructorParameter() { doTest(); }
  @Test
  public void testStringBufferNonNls() { doTest(); }
  @Test
  public void testEnum() { doTest(); }
  @Test
  public void testIgnoredLines() { doTest(); }
  @Test
  public void testStringMethods() { doTest(); }

  @Test
  public void testVarargNonNlsParameter() { doTest(); }
  @Test
  public void testInitializerInAnonymousClass() { doTest(); }
  @Test
  public void testNonNlsArray() { doTest(); }
  @Test
  public void testNonNlsEquals() { doTest(); }
  @Test
  public void testNonNlsTernary() { doTest(); }
  @Test
  public void testParameterInNewAnonymousClass() { doTest(); }
  @Test
  public void testConstructorCallOfNonNlsVariable() { doTest(); }
  public void _testConstructorChains() { doTest(); }
  @Test
  public void testSwitchOnNonNlsString() { doTest(); }
  @Test
  public void testNestedArrayParenthesized() { doTest(); }
  @Test
  public void testNonNlsComment() {
    myTool.setNonNlsCommentPattern("MYNON-NLS");
    doTest();
  }
  @Test
  public void testPropagateToInterfaceMethod() {
    doTest();
  }

  @Test
  public void testNlsOnly() {
    doTestNlsMode();
  }
  
  @Test
  public void testNlsOnlyTernary() {
    doTestNlsMode();
  }
  
  @Test
  public void testNlsOnlyFields() {
    doTestNlsMode();
  }

  @Test
  public void testNlsPackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("package-info.java", """
      @Nls
      package foo;
      import org.jetbrains.annotations.Nls;""");
    doTestNlsMode();
  }

  @Test
  public void testAnnotationArgument() { doTest(); }
  @Test
  public void testAssertionStmt() { doTest(); }
  @Test
  public void testPropertyKeyAnnotated() {
    String oldPattern = myTool.nonNlsCommentPattern;
    try {
      myTool.setNonNlsLiteralPattern("");
      doTest();
    }
    finally {
      myTool.setNonNlsLiteralPattern(oldPattern);
    }
  }
  @Test
  public void testExceptionCtor() { doTest(); }
  @Test
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

  @Test
  public void testEnumConstantIgnored() {
    boolean oldState = myTool.setIgnoreForEnumConstants(true);
    try {
      doTest();
    }
    finally {
      myTool.setIgnoreForEnumConstants(oldState);
    }
  }
  
  @Test
  public void testNlsTypeUse() { doTestNlsMode(); }

  @Test
  public void testNonNlsIndirect() { doTest(); }

  @Test
  public void testNlsIndirect() { doTestNlsMode(); }
  
  @Test
  public void testNonNlsMeta() { doTest(); }
  
  @Test
  public void testNlsMeta() { doTestNlsMode(); }
  
  @Test
  public void testUseConstant() { doTest(); }

  @Test
  public void testNonNlsOnContainer() { doTest(); }
  
  @Test
  public void testUseConstantNls() { doTestNlsMode(); }
  
  @Test
  public void testHtmlEntitiesNlsMode() {
    doTestNlsMode();
  }

  @Test
  public void testArrayInitializerInNlsMode() { doTestNlsMode(); }

  @Test
  public void testSwitchInNlsMode() { doTestNlsMode(); }

  @Test
  public void testReturnCallWithArgs() { doTest(); }
  
  @Test
  public void testConverterMethods() { doTestNlsMode(); }
  
  @Test
  public void testPassthroughMethods() { doTestNlsMode(); }
  
  @Test
  public void testNlsSafeStringBuilder() { doTestNlsMode(); }
  
  @Test
  public void testUnannotatedReferencesQualified() { doTestNlsMode(); }
  
  @Test
  public void testCharSequenceMethods() { doTestNlsMode(); }
  
  @Test
  public void testRefsNonNlsMode() { doTest(); }
  
  @Test
  public void testRefsMetaAnnotation() { doTest(); }
  
  @Test
  public void testStringBuilderAppend() { doTest(); }
  
  @Test
  public void testComputeIfAbsent() { doTest(); }
  
  @Test
  public void testNonNlsFieldInitializer() { doTest(); }

  @Test
  public void testQualifiedReferences() { doTest(); }

  @Test
  public void testLoops() { doTest(); }
}
