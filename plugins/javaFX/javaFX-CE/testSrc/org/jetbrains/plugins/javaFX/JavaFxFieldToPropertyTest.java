package org.jetbrains.plugins.javaFX;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.codeInsight.JavaFxFieldToPropertyIntention;
import org.jetbrains.plugins.javaFX.fxml.AbstractJavaFXTestCase;
import org.jetbrains.plugins.javaFX.packaging.JavaFxApplicationArtifactType;

import java.util.List;

/**
 * @author Pavel.Dolgov
 */
public class JavaFxFieldToPropertyTest extends DaemonAnalyzerTestCase {
  private static final String actionName = JavaFxFieldToPropertyIntention.FAMILY_NAME;

  @Override
  protected void setUpModule() {
    super.setUpModule();
    AbstractJavaFXTestCase.addJavaFxJarAsLibrary(getModule());
    if (isArtifactNeeded()) {
      ArtifactManager.getInstance(getProject()).addArtifact("fake-javafx", JavaFxApplicationArtifactType.getInstance(), null);
    }
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

  public void testFxmlPresenceFieldToProperty() throws Exception {
    doTest(getTestName(false) + ".java", "sample.fxml");
  }

  public void testArtifactPresenceFieldToProperty() throws Exception {
    doTest();
  }

  public void testTwoFilesFieldToProperty() throws Exception {
    final String testName = getTestName(false);
    final String secondFileName = testName + "SecondFile.java";
    doTest(testName + ".java", secondFileName);

    final String expected = StringUtil.convertLineSeparators(VfsUtilCore.loadText(getVirtualFile(testName + "SecondFile_after.java")));
    final PsiClass secondFileClass = JavaPsiFacade.getInstance(myProject).findClass("DoubleDemo2", GlobalSearchScope.allScope(myProject));
    final String actual = secondFileClass.getContainingFile().getText();
    assertEquals("Text mismatch[" + secondFileName + "]", expected, actual);
  }

  private void doTest() throws Exception {
    doTest(getTestName(false) + ".java");
  }

  private void doTest(String... fileNames) throws Exception {
    LanguageLevelProjectExtension.getInstance(myProject).setLanguageLevel(LanguageLevel.JDK_1_7);

    configureByFiles(null, fileNames);
    final IntentionAction intentionAction = getIntentionAction();
    assertNotNull(intentionAction);

    Editor editor = getEditor();
    PsiFile file = getFile();

    assertTrue(CodeInsightTestFixtureImpl.invokeIntention(intentionAction, file, editor, actionName));
    checkResultByFile(getTestName(false) + "_after.java");
  }

  protected IntentionAction getIntentionAction() {
    final List<HighlightInfo> infos = doHighlighting();
    final Editor editor = getEditor();
    final PsiFile file = getFile();
    return findIntentionAction(infos, actionName, editor, file);
  }

  protected boolean isArtifactNeeded() {
    return true;
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/intentions/fieldToProperty/";
  }
}
