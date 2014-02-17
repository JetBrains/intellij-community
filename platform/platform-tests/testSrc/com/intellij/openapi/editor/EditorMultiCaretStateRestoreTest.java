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
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.HeavyFileEditorManagerTestCase;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EditorTestUtil;
import org.jetbrains.annotations.NotNull;

public class EditorMultiCaretStateRestoreTest extends HeavyFileEditorManagerTestCase {
  public void testRestoreState() throws Exception {
    EditorTestUtil.enableMultipleCarets();
    try {
      String text = "some<caret> text<caret>\n" +
                    "some <selection><caret>other</selection> <selection>text<caret></selection>\n" +
                    "<selection>ano<caret>ther</selection> line";
      PsiFile psiFile = myFixture.configureByText(PlainTextFileType.INSTANCE, text);
      VirtualFile virtualFile = psiFile.getVirtualFile();
      assertNotNull(virtualFile);
      myManager.openFile(virtualFile, false);
      myManager.closeAllFiles();
      FileEditor[] fileEditors = myManager.openFile(virtualFile, false);
      assertNotNull(fileEditors);
      assertEquals(1, fileEditors.length);
      Editor editor = ((TextEditor)fileEditors[0]).getEditor();

      verifyEditorState(editor, text);
    }
    finally {
      EditorTestUtil.disableMultipleCarets();
    }
  }

  private static void verifyEditorState(Editor editor, String textWithMarkup) {
    final Document document = new DocumentImpl(textWithMarkup);
    EditorTestUtil.CaretsState caretAndSelectionState = new WriteCommandAction<EditorTestUtil.CaretsState>(null) {
      @Override
      protected void run(@NotNull Result<EditorTestUtil.CaretsState> result) throws Throwable {
        result.setResult(EditorTestUtil.extractCaretAndSelectionMarkers(document));
      }
    }.execute().getResultObject();
    assertEquals(document.getCharsSequence().toString(), editor.getDocument().getText());
    EditorTestUtil.verifyCaretAndSelectionState(editor, caretAndSelectionState);
  }
}
