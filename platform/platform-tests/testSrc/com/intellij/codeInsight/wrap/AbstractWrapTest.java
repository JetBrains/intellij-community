// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.wrap;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.editor.impl.EditorModelFixture;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

public abstract class AbstractWrapTest {
  @SuppressWarnings("deprecation")
  protected static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  protected EditorModelFixture myFixture;

  protected CodeStyleSettings mySettings;

  @BeforeEach
  void setUpAbstractWrapTest() {
    myFixture = editorFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      mySettings = CodeStyle.createTestSettings(CodeStyle.getSettings(myFixture.getProject()));
      mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true;

      CodeStyleSettingsManager.getInstance(myFixture.getProject()).setTemporarySettings(mySettings);
    });
  }

  @AfterEach
  void tearDownAbstractWrapTest() {
    EdtTestUtil.runInEdtAndWait(() -> CodeStyleSettingsManager.getInstance(myFixture.getProject()).dropTemporarySettings());
  }

  protected void checkWrapOnTyping(@NotNull FileType fileType,
                                   @NotNull String textToType,
                                   @NotNull String initial,
                                   @NotNull String expected) {
    String name = "test." + fileType.getDefaultExtension();
    myFixture.configureFromFileText(name, initial);
    EditorModelFixture.assertFileTypeResolved(fileType, name);
    myFixture.type(textToType);
    myFixture.checkResultByText(expected);
  }
}
