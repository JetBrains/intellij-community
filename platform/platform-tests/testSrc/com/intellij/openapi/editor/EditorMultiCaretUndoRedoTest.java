/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
package com.intellij.openapi.editor;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorModelFixture;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.CurrentEditorProvider;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class EditorMultiCaretUndoRedoTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  private EditorModelFixture myFixture;


  @BeforeEach
  void setUp() {
    myFixture = editorFixture.get();
  }

  @AfterEach
  void tearDown() {
    getUndoManager().setOverriddenEditorProvider(null);
  }

  // Not in a command, as isRunInCommand() returned false in JUnit 3
  private static <E extends Throwable> void runTest(@NotNull ThrowableRunnable<E> test) throws E {
    EdtTestUtil.runInEdtAndWait(test);
  }

  @Test
  public void testUndoRedo() {
    runTest(() -> {
      WriteAction.run(()-> {
        init("""
               some<caret> text<caret>
               some <selection><caret>other</selection> <selection>text<caret></selection>
               <selection>ano<caret>ther</selection> line""");
        myFixture.type('A');
        myFixture.executeAction("EditorDelete");
        myFixture.mouse().clickAt(0, 1);
        undo();
        checkResult("someA<caret>textA<caret>some A<caret>A<caret>A<caret>line");
        undo();
        checkResult("""
                      someA<caret> textA<caret>
                      some A<caret> A<caret>
                      A<caret> line""");
        undo();
        checkResult("""
                      some<caret> text<caret>
                      some <selection><caret>other</selection> <selection>text<caret></selection>
                      <selection>ano<caret>ther</selection> line""");
        redo();
        checkResult("""
                      someA<caret> textA<caret>
                      some A<caret> A<caret>
                      A<caret> line""");
      });
    });
  }

  @Test
  public void testBlockSelectionStateAfterUndo() {
    runTest(() -> {
      WriteAction.run(()-> {
        init("a");
        ((EditorEx)myFixture.getEditor()).setColumnMode(true);
        myFixture.mouse().clickAt(0, 2);
        myFixture.type('b');
        undo();
        myFixture.executeAction("EditorRightWithSelection");
        myFixture.verifyCaretsAndSelections(0, 3, 2, 3);
      });
    });
  }

  @Test
  public void testBlockSelectionStateAfterUndo2() {
    runTest(() -> {
      WriteAction.run(() -> {
        init("a");
        ((EditorEx)myFixture.getEditor()).setColumnMode(true);
        myFixture.mouse().pressAt(0, 0).dragTo(0, 2).release();
        myFixture.type('b');
        undo();
        myFixture.verifyCaretsAndSelections(0, 2, 0, 2);
      });
    });
  }

  @Test
  public void testPrimaryCaretPositionAfterUndo() {
    runTest(() -> {
      WriteAction.run(() -> {
        init("line1\n" +
             "line2");
        myFixture.mouse().alt().pressAt(1, 1).dragTo(0, 0).release();
        myFixture.type(' ');
        undo();
        assertEquals(new LogicalPosition(0, 0), myFixture.getEditor().getCaretModel().getPrimaryCaret().getLogicalPosition());
      });
    });
  }

  private void checkResult(final String text) {
    CommandProcessor.getInstance().runUndoTransparentAction(() -> myFixture.checkResultByText(text));
  }

  private void undo() {
    getUndoManager().undo(getTextEditor());
  }

  private void redo() {
    getUndoManager().redo(getTextEditor());
  }

  private UndoManagerImpl getUndoManager() {
    return (UndoManagerImpl) UndoManager.getInstance(myFixture.getProject());
  }

  private TextEditor getTextEditor() {
    return TextEditorProvider.getInstance().getTextEditor(myFixture.getEditor());
  }

  private void init(String text) {
    myFixture.init(text, PlainTextFileType.INSTANCE);
    myFixture.setEditorVisibleSize(1000, 1000);
    getUndoManager().setOverriddenEditorProvider(new CurrentEditorProvider() {
      @Override
      public FileEditor getCurrentEditor(@Nullable Project project) {
        return getTextEditor();
      }
    });
  }
}
