// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.XmlUnresolvedReferenceInspection;
import com.intellij.openapi.application.PluginPathManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class JavaFXImportTest extends DaemonAnalyzerTestCase {
  @Override
  protected void setUpModule() {
    super.setUpModule();
    AbstractJavaFXTestCase.addJavaFxJarAsLibrary(getModule());
    enableInspectionTool(new XmlUnresolvedReferenceInspection());
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
