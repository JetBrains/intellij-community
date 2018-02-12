/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInspection.i18n;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.testFramework.InspectionTestCase;

/**
 * @author lesya
 */
public class I18NInspectionTest extends InspectionTestCase {
  private void doTest() {
    doTest(new I18nInspection());
  }
  private void doTest(I18nInspection tool) {
    doTest("i18n/" + getTestName(true), tool);
  }

  public void testHardCodedStringLiteralAsParameter() { doTest(); }
  public void testReturnTypeInheritsNonNlsAnnotationFromParent() { doTest(); }
  public void testRecursiveInheritance() { doTest(); }
  public void testParameterInheritsNonNlsAnnotationFromSuper() { doTest(); }
  public void testLocalVariables() { doTest(); }
  public void testFields() { doTest(); }
  public void testAnonymousClassConstructorParameter() { doTest(); }
  public void testStringBufferNonNls() { doTest(); }
  public void testEnum() {
     final JavaPsiFacade facade = getJavaFacade();
     final LanguageLevel effectiveLanguageLevel = LanguageLevelProjectExtension.getInstance(facade.getProject()).getLanguageLevel();
     LanguageLevelProjectExtension.getInstance(facade.getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
     try {
       doTest();
     }
     finally {
       LanguageLevelProjectExtension.getInstance(facade.getProject()).setLanguageLevel(effectiveLanguageLevel);
     }
   }

  public void testVarargNonNlsParameter() { doTest(); }
  public void testInitializerInAnonymousClass() { doTest(); }
  public void testNonNlsArray() { doTest(); }
  public void testParameterInNewAnonymousClass() { doTest(); }
  public void testConstructorCallOfNonNlsVariable() { doTest(); }
  public void testSwitchOnNonNlsString() { doTest(); }
  public void testNonNlsComment() {
    I18nInspection inspection = new I18nInspection();
    inspection.nonNlsCommentPattern = "MYNON-NLS";
    inspection.cacheNonNlsCommentPattern();
    doTest(inspection);
  }
  public void testAnnotationArgument() { doTest(); }

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("java-i18n") + "/testData/inspections";
  }
}
