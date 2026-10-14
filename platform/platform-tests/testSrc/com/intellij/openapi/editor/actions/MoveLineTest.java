/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.actions;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorModelFixture;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

@TestApplication
public class MoveLineTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  private EditorModelFixture myFixture;


  @BeforeEach
  void setUp() {
    myFixture = editorFixture.get();
  }

  @Test
  public void testInlaysAreMovedWithText() {
    myFixture.runTest(() -> {
      myFixture.initText("<caret>ab\ncd\n");
      myFixture.addInlay(1);
      myFixture.executeAction(IdeActions.ACTION_MOVE_LINE_DOWN_ACTION);
      myFixture.checkResultByText("cd\n<caret>ab\n");
      assertInlayPositions(4);
    });
  }

  @Test
  public void testMoveWithMultipleCarets() {
    myFixture.runTest(() -> {
      myFixture.initText("<caret>foo\nbar\nba<caret>z\nfuz\n");
      myFixture.executeAction(IdeActions.ACTION_MOVE_LINE_DOWN_ACTION);
      myFixture.checkResultByText("bar\n<caret>foo\nfuz\nba<caret>z\n");
    });
  }

  @Test
  public void testMoveWithMultipleCaretsOnTheSameLine() {
    myFixture.runTest(() -> {
      myFixture.initText("<caret>f<caret>o<caret>o\nbar\nbaz\nfuz\n");
      myFixture.executeAction(IdeActions.ACTION_MOVE_LINE_DOWN_ACTION);
      myFixture.checkResultByText("bar\n<caret>f<caret>o<caret>o\nbaz\nfuz\n");
    });
  }

  private void assertInlayPositions(int... offsets) {
    Editor editor = myFixture.getEditor();
    int[] actualPositions = editor.getInlayModel().getInlineElementsInRange(0, editor.getDocument().getTextLength())
      .stream().mapToInt(i -> i.getOffset()).toArray();
    assertArrayEquals(offsets, actualPositions);
  }
}
