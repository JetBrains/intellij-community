package org.jetbrains.plugins.javaFX;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.AbstractJavaFXTestCase;
import org.jetbrains.plugins.javaFX.packaging.JavaFxApplicationArtifactType;

import java.util.List;

/**
 * @author Pavel.Dolgov
 * Run this test with 'main_idea_tests' classpath
 */
public class JavaFxFieldToPropertyTest extends DaemonAnalyzerTestCase {
  private static final String actionName = "Convert to JavaFX property";

  @Override
  protected void setUpModule() {
    super.setUpModule();
    AbstractJavaFXTestCase.addJavaFxJarAsLibrary(getModule());
    ArtifactManager.getInstance(getProject()).addArtifact("fake-javafx", JavaFxApplicationArtifactType.getInstance(), null);
  }

  public void testIntFieldToProperty() throws Exception {
    doTest();
  }

  public void testBoxedFloatFieldToProperty() throws Exception {
    doTest();
  }

  public void testStringFieldToProperty() throws Exception {
    doTest();
  }

  public void testListFieldToProperty() throws Exception {
    doTest();
  }

  public void testBigDecimalFieldToProperty() throws Exception {
    doTest(true);
  }

  public void testLongFieldToProperty() throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    doTest(false);
  }

  private void doTest(boolean withFxml) throws Exception {
    final IntentionAction intentionAction = getIntentionAction(withFxml);
    assertNotNull(intentionAction);

    Editor editor = getEditor();
    PsiFile file = getFile();

    assertTrue(ShowIntentionActionsHandler.chooseActionAndInvoke(file, editor, intentionAction,actionName));
    checkResultByFile(getTestName(false) + "_after.java");
  }

  protected IntentionAction getIntentionAction(boolean withFxml) throws Exception {
    if (withFxml) {
      configureByFiles(null, getTestName(false) + ".java", "sample.fxml");
    }
    else {
      configureByFiles(null, getTestName(false) + ".java");
    }
    final List<HighlightInfo> infos = doHighlighting();
    final Editor editor = getEditor();
    final PsiFile file = getFile();
    return findIntentionAction(infos, actionName, editor, file);
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/intentions/fieldToProperty/";
  }

}
