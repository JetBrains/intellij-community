// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX;

import com.intellij.codeInsight.daemon.DaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFixture;
import org.jetbrains.plugins.javaFX.packaging.JavaFxApplicationArtifactType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static com.intellij.codeInsight.daemon.DaemonAnalyzerFixture.daemonAnalyzerFixture;
import static com.intellij.codeInsight.daemon.DaemonAnalyzerFixture.findIntentionAction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class JavaFxFieldToPropertyTest {
  protected final TestFixture<DaemonAnalyzerFixture> daemonFixture = daemonAnalyzerFixture(getTestDataPath());

  @BeforeEach
  void setUp() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    JavaFxFixture.addJavaFxJarAsLibrary(fixture.getModule());
    if (isArtifactNeeded()) {
      WriteAction.runAndWait(
        () -> ArtifactManager.getInstance(fixture.getProject()).addArtifact("fake-javafx", JavaFxApplicationArtifactType.getInstance(), null));
    }
  }

  @Test
  public void testIntFieldToProperty() {
    doTest();
  }

  @Test
  public void testBoxedFloatFieldToProperty() {
    doTest();
  }

  @Test
  public void testStringFieldToProperty() {
    doTest();
  }

  @Test
  public void testListFieldToProperty() {
    doTest();
  }

  @Test
  public void testFxmlPresenceFieldToProperty() {
    doTest(daemonFixture.get().getTestName(false) + ".java", "sample.fxml");
  }

  @Test
  public void testArtifactPresenceFieldToProperty() {
    doTest();
  }

  @Test
  public void testTwoFilesFieldToProperty() throws IOException {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    Project myProject = fixture.getProject();
    final String testName = fixture.getTestName(false);
    final String secondFileName = testName + "SecondFile.java";
    doTest(testName + ".java", secondFileName);

    final String expected = StringUtil.convertLineSeparators(VfsUtilCore.loadText(fixture.findVirtualFile(testName + "SecondFile_after.java")));
    final PsiClass secondFileClass = ReadAction.computeBlocking(
      () -> JavaPsiFacade.getInstance(myProject).findClass("DoubleDemo2", GlobalSearchScope.allScope(myProject)));
    final String actual = ReadAction.computeBlocking(() -> secondFileClass.getContainingFile().getText());
    assertEquals(expected, actual, "Text mismatch[" + secondFileName + "]");
  }

  private void doTest() {
    doTest(daemonFixture.get().getTestName(false) + ".java");
  }

  private void doTest(String... fileNames) {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    IdeaTestUtil.setProjectLanguageLevel(fixture.getProject(), LanguageLevel.JDK_1_7);

    fixture.configureByFiles(null, fileNames);
    final IntentionAction intentionAction = getIntentionAction();
    assertNotNull(intentionAction);

    Editor editor = fixture.getEditor();
    PsiFile file = fixture.getFile();

    EdtTestUtil.runInEdtAndWait(() -> CodeInsightTestFixtureImpl.invokeIntention(intentionAction, file, editor));
    fixture.checkResultByFile(fixture.getTestName(false) + "_after.java");
  }

  protected IntentionAction getIntentionAction() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    final List<HighlightInfo> infos = fixture.doHighlighting();
    final Editor editor = fixture.getEditor();
    final PsiFile file = fixture.getFile();
    return EdtTestUtil.runInEdtAndGet(
      () -> findIntentionAction(infos, JavaFXBundle.message("intention.family.name.convert.to.javafx.property"), editor, file));
  }

  protected boolean isArtifactNeeded() {
    return true;
  }

  private static @NotNull String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/intentions/fieldToProperty/";
  }
}
