// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl;
import com.intellij.openapi.editor.impl.EditorModelFixture;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

public abstract class EditingTestBase {
  @SuppressWarnings("deprecation")
  protected static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  protected EditorModelFixture myFixture;

  protected CodeStyleSettings mySettings;
  private EditorColorsScheme mySavedScheme;
  private EditorColorsScheme myTestScheme;
  private String myTestMethodName;

  @BeforeEach
  void setUpEditingTestBase(TestInfo testInfo) {
    myFixture = editorFixture.get();
    myTestMethodName = testInfo.getTestMethod().orElseThrow().getName();
    myFixture.getFixture().setTestDataPath(getTestDataPath());
    EdtTestUtil.runInEdtAndWait(() -> {
      mySettings = CodeStyle.createTestSettings(CodeStyle.getSettings(myFixture.getProject()));
      CodeStyleSettingsManager.getInstance(myFixture.getProject()).setTemporarySettings(mySettings);

      mySavedScheme = EditorColorsManager.getInstance().getGlobalScheme();
      myTestScheme = (EditorColorsScheme)mySavedScheme.clone();
      myTestScheme.setName("EditingTest.testScheme");
      EditorColorsManager.getInstance().addColorScheme(myTestScheme);
      EditorColorsManager.getInstance().setGlobalScheme(myTestScheme);
    });
  }

  @AfterEach
  void tearDownEditingTestBase() {
    EdtTestUtil.runInEdtAndWait(() -> {
      CodeStyleSettingsManager.getInstance(myFixture.getProject()).dropTemporarySettings();

      EditorColorsManager.getInstance().setGlobalScheme(mySavedScheme);
      ((EditorColorsManagerImpl)EditorColorsManager.getInstance()).getSchemeManager().removeScheme(myTestScheme);
    });
  }

  /**
   * Returns the test data root for {@link #configureByFile} and {@link #checkResultByFile}.
   */
  protected @NonNls @NotNull String getTestDataPath() {
    return PathManagerEx.getTestDataPath();
  }

  protected @NotNull String getTestName(boolean lowercaseFirstLetter) {
    return PlatformTestUtil.getTestName(myTestMethodName, lowercaseFirstLetter);
  }

  protected @NotNull CodeStyleSettings getCurrentCodeStyleSettings() {
    return CodeStyle.getSettings(myFixture.getProject());
  }

  protected void configureByFile(@NonNls @NotNull String relativePath) {
    myFixture.getFixture().configureByFile(relativePath);
  }

  protected void checkResultByFile(@NonNls @NotNull String expectedFilePath) {
    myFixture.getFixture().checkResultByFile(expectedFilePath);
  }

  protected void doTest(@NonNls FileType fileType, final char ... chars) {
    doTest(fileType, () -> {
      for (char c : chars) {
        myFixture.type(c);
      }
    });
  }

  protected void doTest(@NonNls FileType fileType, Runnable action) {
    CommonCodeStyleSettings.IndentOptions indentOptions = mySettings.getIndentOptions(null);
    indentOptions.USE_TAB_CHARACTER = true;
    indentOptions.INDENT_SIZE = 4;
    indentOptions.TAB_SIZE = 4;
    indentOptions.CONTINUATION_INDENT_SIZE = 4;
    UISettings.getInstance().setFontFace("Tahoma");
    UISettings.getInstance().setFontSize(11);

    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    scheme.setEditorFontName("Tahoma");
    scheme.setEditorFontSize(11);

    String path = "/codeInsight/editing/before" + getTestName(false) + "."+fileType.getDefaultExtension();
    configureByFile(path);
    EditorModelFixture.assertFileTypeResolved(fileType, path);
    action.run();
    checkResultByFile("/codeInsight/editing/after" + getTestName(false) + "."+fileType.getDefaultExtension());
  }
}
