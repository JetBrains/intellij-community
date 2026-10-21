// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.editor.actions;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.impl.EditorModelFixture;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@TestApplication
public class UndoDeleteLineTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  private EditorModelFixture myFixture;


  @BeforeEach
  void setUp() {
    myFixture = editorFixture.get();
  }

  // Not in a command: otherwise undoing doesn't work
  private static <E extends Throwable> void runTest(@NotNull ThrowableRunnable<E> test) throws E {
    EdtTestUtil.runInEdtAndWait(test);
  }

  @Test
  public void test() {
    runTest(() -> {
      String before = """
        1
        2
        3<caret>""";

      String after = """
        1
        2<caret>""";

      myFixture.configureFromFileText(myFixture.getTestName() + ".txt", before);
      myFixture.deleteLine();
      myFixture.deleteLine();
      myFixture.executeAction(IdeActions.ACTION_UNDO);
      myFixture.checkResultByText(after);
    });
  }
}
