// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.impl.EditorModelFixture;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;

import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

public abstract class AbstractMoveElementLeftRightTest {
  @SuppressWarnings("deprecation")
  protected static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  protected EditorModelFixture myFixture;

  @BeforeEach
  void setUpAbstractMoveElementLeftRightTest() {
    myFixture = editorFixture.get();
  }

  protected void doTestFromLeftToRight(String leftMostPosition, String... rightPositions) {
    doTest(true, leftMostPosition);
    doTest(false, leftMostPosition, rightPositions);
  }

  protected void doTestFromRightToLeft(String rightMostPosition, String... leftPositions) {
    doTest(false, rightMostPosition);
    doTest(true, rightMostPosition, leftPositions);
  }

  private void doTest(boolean moveLeft, String before, String... after) {
    String current = before;
    for (String next : after) {
      doTestSingle(moveLeft, current, next);
      current = next;
    }
    doTestSingle(moveLeft, current, current);
    if (after.length == 0) return;
    for (int i = after.length - 2; i >= 0; i--) {
      String prev = after[i];
      doTestSingle(!moveLeft, current, prev);
      current = prev;
    }
    doTestSingle(!moveLeft, current, before);
  }

  protected void doTestSingle(boolean moveLeft, String before, String after) {
    configureEditor(before);
    myFixture.executeAction(moveLeft ? IdeActions.MOVE_ELEMENT_LEFT : IdeActions.MOVE_ELEMENT_RIGHT);
    myFixture.checkResultByText(after);
  }

  protected abstract void configureEditor(String contents);
}
