/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.impl.CurrentEditorProvider;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.testFramework.TestFileType;
import org.jetbrains.annotations.NotNull;

public class EditorMultiCaretUndoRedoTest extends AbstractEditorTest {
  private CurrentEditorProvider mySavedCurrentEditorProvider;

  public void setUp() throws Exception {
    super.setUp();
    mySavedCurrentEditorProvider = getUndoManager().getEditorProvider();
  }

  public void tearDown() throws Exception {
    getUndoManager().setEditorProvider(mySavedCurrentEditorProvider);
    super.tearDown();
  }

  @Override
  // disabling execution of tests in command
  protected void runTest() {
    new WriteAction<Void>() {
      @Override
      protected void run(@NotNull Result<Void> result) throws Throwable {
        doRunTest();
      }
    }.execute();
  }

  public void testUndoRedo() {
    init("some<caret> text<caret>\n" +
         "some <selection><caret>other</selection> <selection>text<caret></selection>\n" +
         "<selection>ano<caret>ther</selection> line");
    type('A');
    executeAction("EditorDelete");
    mouse().clickAt(0, 1);
    undo();
    checkResult("someA<caret>textA<caret>some A<caret>A<caret>A<caret>line");
    undo();
    checkResult("someA<caret> textA<caret>\n" +
                      "some A<caret> A<caret>\n" +
                      "A<caret> line");
    undo();
    checkResult("some<caret> text<caret>\n" +
                      "some <selection><caret>other</selection> <selection>text<caret></selection>\n" +
                      "<selection>ano<caret>ther</selection> line");
    redo();
    checkResult("someA<caret> textA<caret>\n" +
                      "some A<caret> A<caret>\n" +
                      "A<caret> line");
  }

  public void testBlockSelectionStateAfterUndo() {
    init("a");
    ((EditorEx)myEditor).setColumnMode(true);
    mouse().clickAt(0, 2);
    type('b');
    undo();
    executeAction("EditorRightWithSelection");
    verifyCaretsAndSelections(0, 3, 2, 3);
  }

  public void testBlockSelectionStateAfterUndo2() {
    init("a");
    ((EditorEx)myEditor).setColumnMode(true);
    mouse().pressAt(0, 0).dragTo(0, 2).release();
    type('b');
    undo();
    verifyCaretsAndSelections(0, 2, 0, 2);
  }

  public void testPrimaryCaretPositionAfterUndo() {
    init("line1\n" +
         "line2");
    mouse().alt().pressAt(1, 1).dragTo(0, 0).release();
    type(' ');
    undo();
    assertEquals(new LogicalPosition(0, 0), myEditor.getCaretModel().getPrimaryCaret().getLogicalPosition());
  }

  private void checkResult(final String text) {
    CommandProcessor.getInstance().runUndoTransparentAction(() -> checkResultByText(text));
  }

  private static void undo() {
    getUndoManager().undo(getTextEditor());
  }

  private static void redo() {
    getUndoManager().redo(getTextEditor());
  }

  private static UndoManagerImpl getUndoManager() {
    return (UndoManagerImpl) UndoManager.getInstance(ourProject);
  }

  private static TextEditor getTextEditor() {
    return TextEditorProvider.getInstance().getTextEditor(myEditor);
  }

  private void init(String text) {
    init(text, TestFileType.TEXT);
    setEditorVisibleSize(1000, 1000);
    getUndoManager().setEditorProvider(new CurrentEditorProvider() {
      @Override
      public FileEditor getCurrentEditor() {
        return getTextEditor();
      }
    });
  }
}
