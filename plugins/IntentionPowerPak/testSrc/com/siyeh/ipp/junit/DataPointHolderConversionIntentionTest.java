// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.junit;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Batkovich
 */
public class DataPointHolderConversionIntentionTest extends LightQuickFixTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(getProject());
    settings.STATIC_FIELD_NAME_PREFIX = "qwe";
    settings.STATIC_FIELD_NAME_SUFFIX = "asd";
  }

  private void doTest() {
    doSingleTest(getTestName(false) + ".java");
  }

  public void testField() {
    doTest();
  }

  public void testField2() {
    doTest();
  }

  public void testMethod() {
    doTest();
  }

  public void testMethod2() {
    doTest();
  }

  public void testMethod3() {
    doTest();
  }

  public void testMethod4() {
    doTest();
  }

  public void testMethod5() {
    doTest();
  }

  public void testNameTyping() {
    configureByFile(getBasePath() + "/beforeNameTyping.java");
    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
    doAction("Replace by @DataPoint method");
    final TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
    assertNotNull(state);
    type("typedMethodNameFromTemplates");
    state.nextTab();
    assertTrue(state.isFinished());
    checkResultByFile(getBasePath() + "/afterNameTyping.java");
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("IntentionPowerPak") + "/test/com/siyeh/ipp/junit/";
  }

  @Override
  protected String getBasePath() {
    return "dataPointHolders";
  }
}
