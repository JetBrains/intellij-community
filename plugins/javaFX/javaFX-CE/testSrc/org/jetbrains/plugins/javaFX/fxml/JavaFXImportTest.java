/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class JavaFXImportTest extends DaemonAnalyzerTestCase {
  @Override
  protected void setUpModule() {
    super.setUpModule();
    PsiTestUtil.addLibrary(getModule(), "javafx", PluginPathManager.getPluginHomePath("javaFX") + "/testData", "jfxrt.jar");
  }

  public void testSimpleImport() throws Exception {
    doTest();
  }

  public void testTextField() throws Exception {
    configureByFiles(null, getTestName(true) + ".fxml", getTestName(false) + ".java");
    final List<HighlightInfo> infos = doHighlighting();
    findAndInvokeIntentionAction(infos, "Import class", getEditor(), getFile());
    checkResultByFile(getTestName(true) + "_after.fxml");
  }

  public void testInsets() throws Exception {
    configureByFiles(null, getTestName(true) + ".fxml", getTestName(false) + ".java");
    final List<HighlightInfo> infos = doHighlighting();
    findAndInvokeIntentionAction(infos, "Import class", getEditor(), getFile());
    checkResultByFile(getTestName(true) + "_after.fxml");
  }

  private void doTest() throws Exception {
    configureByFiles(null, getTestName(true) + ".fxml");
    final List<HighlightInfo> infos = doHighlighting();
    findAndInvokeIntentionAction(infos, "Import class", getEditor(), getFile());
    checkResultByFile(getTestName(true) + "_after.fxml");
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/importing/";
  }
}
