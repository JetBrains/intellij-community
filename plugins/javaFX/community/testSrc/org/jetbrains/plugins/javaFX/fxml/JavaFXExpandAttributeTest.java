// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class JavaFXExpandAttributeTest extends DaemonAnalyzerTestCase {
  @Override
  protected void setUpModule() {
    super.setUpModule();
    AbstractJavaFXTestCase.addJavaFxJarAsLibrary(getModule());
  }

  public void testDefaultAttr() throws Exception {
    doTest(false, "fx:id");
  }

  public void testSimple() throws Exception {
    doTest(true, "disable");
  }

  public void testStaticAttr() throws Exception {
    doTest(true, "GridPane.columnIndex");
  }

  public void testExpandMultipleVals2List() throws Exception {
    doTest(true, "stylesheets");
  }

  public void testExpandVal2List() throws Exception {
    doTest(true, "stylesheets");
  }

  private void doTest(boolean available, final String attrName) throws Exception {
    configureByFiles(null, getTestName(true) + ".fxml");
    
    final List<HighlightInfo> infos = doHighlighting();
    Editor editor = getEditor();
    PsiFile file = getFile();

    final String actionName = "Expand '" + attrName + "' to tag";
    IntentionAction intentionAction = findIntentionAction(infos, actionName, editor, file);

    if (available) {
      assertNotNull(actionName, intentionAction);
      CodeInsightTestFixtureImpl.invokeIntention(intentionAction, file, editor);
      checkResultByFile(getTestName(true) + "_after.fxml");
    } else {
      assertNull(intentionAction);
    }
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/intentions/expandAttr/";
  }
}
