// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.codeInsight.daemon.DaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.codeInsight.daemon.DaemonAnalyzerFixture.daemonAnalyzerFixture;
import static com.intellij.codeInsight.daemon.DaemonAnalyzerFixture.findIntentionAction;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class JavaFXExpandAttributeTest {
  private final TestFixture<DaemonAnalyzerFixture> daemonFixture = daemonAnalyzerFixture(getTestDataPath());

  @BeforeEach
  void setUp() {
    JavaFxFixture.addJavaFxJarAsLibrary(daemonFixture.get().getModule());
  }

  @Test
  public void testDefaultAttr() {
    doTest(false, "fx:id");
  }

  @Test
  public void testSimple() {
    doTest(true, "disable");
  }

  @Test
  public void testStaticAttr() {
    doTest(true, "GridPane.columnIndex");
  }

  @Test
  public void testExpandMultipleVals2List() {
    doTest(true, "stylesheets");
  }

  @Test
  public void testExpandVal2List() {
    doTest(true, "stylesheets");
  }

  private void doTest(boolean available, final String attrName) {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFiles(null, fixture.getTestName(true) + ".fxml");
    
    final List<HighlightInfo> infos = fixture.doHighlighting();
    Editor editor = fixture.getEditor();
    PsiFile file = fixture.getFile();

    final String actionName = "Expand '" + attrName + "' to tag";
    IntentionAction intentionAction = EdtTestUtil.runInEdtAndGet(() -> findIntentionAction(infos, actionName, editor, file));

    if (available) {
      assertNotNull(intentionAction, actionName);
      EdtTestUtil.runInEdtAndWait(() -> CodeInsightTestFixtureImpl.invokeIntention(intentionAction, file, editor));
      fixture.checkResultByFile(fixture.getTestName(true) + "_after.fxml");
    } else {
      assertNull(intentionAction);
    }
  }

  private static @NotNull String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/intentions/expandAttr/";
  }
}
