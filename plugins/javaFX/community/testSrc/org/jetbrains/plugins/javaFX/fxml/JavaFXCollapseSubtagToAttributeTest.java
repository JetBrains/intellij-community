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

public class JavaFXCollapseSubtagToAttributeTest extends DaemonAnalyzerTestCase {
  @Override
  protected void setUpModule() {
    super.setUpModule();
    AbstractJavaFXTestCase.addJavaFxJarAsLibrary(getModule());
  }

  public void testAdditionalSubtags() throws Exception {
    doTest(false);
  }

  public void testSimple() throws Exception {
    doTest(true);
  }

  public void testStaticAttr() throws Exception {
    doTest(true, "GridPane.rowIndex");
  }

  public void testStyleclass() throws Exception {
    doTest(true, "styleClass");
  }

  private void doTest(boolean available) throws Exception {
    doTest(available, "text");
  }

  private void doTest(boolean available, final String tagName) throws Exception {
    configureByFiles(null, getTestName(true) + ".fxml");
    
    final List<HighlightInfo> infos = doHighlighting();
    Editor editor = getEditor();
    PsiFile file = getFile();
    
    IntentionAction intentionAction = findIntentionAction(infos, "Collapse tag '" + tagName + "' to attribute", editor, file);

    if (available) {
      assertNotNull("Collapse tag '" + tagName + "' to attribute", intentionAction);
      CodeInsightTestFixtureImpl.invokeIntention(intentionAction, file, editor);
      checkResultByFile(getTestName(true) + "_after.fxml");
    } else {
      assertNull(intentionAction);
    }
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/intentions/collapseToAttr/";
  }
}
