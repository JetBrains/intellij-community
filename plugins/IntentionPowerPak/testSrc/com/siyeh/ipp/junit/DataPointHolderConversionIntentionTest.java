/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ipp.junit;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Batkovich
 */
public class DataPointHolderConversionIntentionTest extends LightQuickFixTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final JavaCodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject()).getCustomSettings(JavaCodeStyleSettings.class);
    settings.STATIC_FIELD_NAME_PREFIX = "qwe";
    settings.STATIC_FIELD_NAME_SUFFIX = "asd";
  }

  @Override
  protected void tearDown() throws Exception {
    final JavaCodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject()).getCustomSettings(JavaCodeStyleSettings.class);
    settings.STATIC_FIELD_NAME_PREFIX = "";
    settings.STATIC_FIELD_NAME_SUFFIX = "";
    super.tearDown();
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
    TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable());
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
