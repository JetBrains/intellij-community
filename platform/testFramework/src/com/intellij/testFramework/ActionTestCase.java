/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.testFramework;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import org.jetbrains.annotations.NotNull;

/**
 * User: Andrey.Vokin
 * Date: 11/26/13
 */
public abstract class ActionTestCase extends UsefulTestCase {
  public static final String CARET_TAG_REPLACE_REGEX = EditorTestUtil.CARET_TAG_PREFIX + "(\\+\\d+)?>";
  protected CodeInsightTestFixture myFixture;

  protected abstract FileType getFileType();

  protected abstract String getTestDataPath();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    final TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder = factory.createLightFixtureBuilder(null);
    final IdeaProjectTestFixture fixture = fixtureBuilder.getFixture();
    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture, new LightTempDirTestFixtureImpl(true));
    myFixture.setUp();
    myFixture.setTestDataPath(getTestDataPath());
  }


  @Override
  protected void tearDown() throws Exception {
    myFixture.tearDown();
    myFixture = null;
    super.tearDown();
  }

  protected void doAction(@NotNull final String before, @NotNull String expected, @NotNull final Runnable action) {
    int[] caretAndSelectionPositionInSourceFile = EditorTestUtil.getCaretAndSelectionPosition(before);
    int[] caretAndSelectionPositionInExpectedFile = EditorTestUtil.getCaretAndSelectionPosition(expected);

    final String sourceFinal = before.replaceAll(CARET_TAG_REPLACE_REGEX, "").replace(EditorTestUtil.SELECTION_START_TAG, "").replace(EditorTestUtil.SELECTION_END_TAG, "");
    final String expectedFinal = expected.replaceAll(CARET_TAG_REPLACE_REGEX, "").replace(EditorTestUtil.SELECTION_START_TAG, "").replace(EditorTestUtil.SELECTION_END_TAG, "");

    final PsiFile file = myFixture.configureByText(getFileType(), sourceFinal);
    myFixture.getEditor().getSettings().setVirtualSpace(true);
    if (caretAndSelectionPositionInSourceFile[0] >= 0) {
      CaretModel caretModel = myFixture.getEditor().getCaretModel();
      caretModel.moveToOffset(caretAndSelectionPositionInSourceFile[0]);
      if (caretAndSelectionPositionInSourceFile[1] != 0) {
        int line = caretModel.getVisualPosition().getLine();
        int column = caretModel.getVisualPosition().getColumn();
        caretModel.moveToVisualPosition(new VisualPosition(line, column + caretAndSelectionPositionInSourceFile[1]));
      }
    }

    if (caretAndSelectionPositionInSourceFile[2] >= 0) {
      myFixture.getEditor().getSelectionModel()
        .setSelection(caretAndSelectionPositionInSourceFile[2], caretAndSelectionPositionInSourceFile[3]);
    }

    ApplicationManager.getApplication().runWriteAction(action);

    if (caretAndSelectionPositionInExpectedFile[0] >= 0) {
      assertEquals(caretAndSelectionPositionInExpectedFile[0], myFixture.getCaretOffset());
      if (caretAndSelectionPositionInExpectedFile[1] != 0) {
        assertEquals(caretAndSelectionPositionInExpectedFile[1], myFixture.getEditor().getCaretModel().getVisualPosition().getColumn());
      }
    }
    if (caretAndSelectionPositionInExpectedFile[2] >= 0) {
      assertEquals(caretAndSelectionPositionInExpectedFile[2], myFixture.getEditor().getSelectionModel().getSelectionStart());
      assertEquals(caretAndSelectionPositionInExpectedFile[3], myFixture.getEditor().getSelectionModel().getSelectionEnd());
    }

    assertEquals(expectedFinal, myFixture.getDocument(file).getText());
  }
}
