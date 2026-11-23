// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.codeInsight.daemon.DaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.XmlUnresolvedReferenceInspection;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.codeInsight.daemon.DaemonAnalyzerFixture.daemonAnalyzerFixture;

@TestApplication
public class JavaFXImportTest {
  private final TestFixture<DaemonAnalyzerFixture> daemonFixture = daemonAnalyzerFixture(getTestDataPath());

  @BeforeEach
  void setUp() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    JavaFxFixture.addJavaFxJarAsLibrary(fixture.getModule());
    fixture.enableInspectionTool(new XmlUnresolvedReferenceInspection());
  }

  @Test
  public void testSimpleImport() {
    doTest();
  }

  @Test
  public void testTextField() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFiles(null, fixture.getTestName(true) + ".fxml", fixture.getTestName(false) + ".java");
    final List<HighlightInfo> infos = fixture.doHighlighting();
    fixture.findAndInvokeIntentionAction(infos, "Import class");
    fixture.checkResultByFile(fixture.getTestName(true) + "_after.fxml");
  }

  @Test
  public void testInsets() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFiles(null, fixture.getTestName(true) + ".fxml", fixture.getTestName(false) + ".java");
    final List<HighlightInfo> infos = fixture.doHighlighting();
    fixture.findAndInvokeIntentionAction(infos, "Import class");
    fixture.checkResultByFile(fixture.getTestName(true) + "_after.fxml");
  }

  private void doTest() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFiles(null, fixture.getTestName(true) + ".fxml");
    final List<HighlightInfo> infos = fixture.doHighlighting();
    fixture.findAndInvokeIntentionAction(infos, "Import class");
    fixture.checkResultByFile(fixture.getTestName(true) + "_after.fxml");
  }

  private static @NotNull String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/importing/";
  }
}
