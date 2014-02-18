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
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.TestFileType;
import com.intellij.testFramework.fixtures.EditorScrollingFixture;
import org.jetbrains.annotations.NotNull;

public class EditorMultiCaretUndoRedoTest extends AbstractEditorTest {
  private CurrentEditorProvider mySavedCurrentEditorProvider;

  public void setUp() throws Exception {
    super.setUp();
    EditorTestUtil.enableMultipleCarets();
    mySavedCurrentEditorProvider = getUndoManager().getEditorProvider();
  }

  public void tearDown() throws Exception {
    getUndoManager().setEditorProvider(mySavedCurrentEditorProvider);
    EditorTestUtil.disableMultipleCarets();
    super.tearDown();
  }

  @Override
  // disabling execution of tests in command
  protected void runTest() throws Throwable {
    new WriteAction<Void>() {
      @Override
      protected void run(@NotNull Result<Void> result) throws Throwable {
        doRunTest();
      }
    }.execute();
  }

  public void testUndoRedo() throws Exception {
    init("some<caret> text<caret>\n" +
         "some <selection><caret>other</selection> <selection>text<caret></selection>\n" +
         "<selection>ano<caret>ther</selection> line",
         TestFileType.TEXT);
    setupEditorProvider();
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

  private void checkResult(final String text) {
    CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
      @Override
      public void run() {
        checkResultByText(text);
      }
    });
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

  private static void setupEditorProvider() {
    getUndoManager().setEditorProvider(new CurrentEditorProvider() {
      @Override
      public FileEditor getCurrentEditor() {
        return getTextEditor();
      }
    });
  }
}
