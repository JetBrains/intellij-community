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
  private void doTest() throws Exception {
    doTest(new I18nInspection());
  }
  private void doTest(I18nInspection tool) throws Exception {
    doTest("i18n/" + getTestName(true), tool);
  }

  public void testHardCodedStringLiteralAsParameter() throws Exception{ doTest(); }
  public void testReturnTypeInheritsNonNlsAnnotationFromParent() throws Exception{ doTest(); }
  public void testRecursiveInheritance() throws Exception { doTest(); }
  public void testParameterInheritsNonNlsAnnotationFromSuper() throws Exception { doTest(); }
  public void testLocalVariables() throws Exception { doTest(); }
  public void testFields() throws Exception{ doTest(); }
  public void testAnonymousClassConstructorParameter() throws Exception { doTest(); }
  public void testStringBufferNonNls() throws Exception { doTest(); }
  public void testEnum() throws Exception {
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

  public void testFormTabbedPaneTitle() throws Exception { doTest(); }
  public void testVarargNonNlsParameter() throws Exception { doTest(); }
  public void testInitializerInAnonymousClass() throws Exception{ doTest(); }
  public void testNonNlsArray() throws Exception{ doTest(); }
  public void testParameterInNewAnonymousClass() throws Exception{ doTest(); }
  public void testConstructorCallOfNonNlsVariable() throws Exception{ doTest(); }
  public void testSwitchOnNonNlsString() throws Exception{ doTest(); }
  public void testNonNlsComment() throws Exception{
    I18nInspection inspection = new I18nInspection();
    inspection.nonNlsCommentPattern = "MYNON-NLS";
    inspection.cacheNonNlsCommentPattern();
    doTest(inspection);
  }
  public void testAnnotationArgument() throws Exception{ doTest(); }

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("java-i18n") + "/testData/inspections";
  }
}
