// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.HeavyFileEditorManagerTestCase;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.common.EditorCaretTestUtil;

public class EditorMultiCaretStateRestoreTest extends HeavyFileEditorManagerTestCase {
  public void testRestoreState() {
    String text = """
      some<caret> text<caret>
      some <selection><caret>other</selection> <selection>text<caret></selection>
      <selection>ano<caret>ther</selection> line""";
    PsiFile psiFile = myFixture.configureByText(PlainTextFileType.INSTANCE, text);
    VirtualFile virtualFile = psiFile.getVirtualFile();
    assertNotNull(virtualFile);
    openFile(virtualFile);
    FileEditorManagerEx.getInstanceEx(getProject()).closeAllFiles();
    Editor editor = openFile(virtualFile);

    verifyEditorState(editor, text);
  }

  private Editor openFile(VirtualFile virtualFile) {
    FileEditor[] fileEditors = FileEditorManagerEx.getInstanceEx(getProject()).openFile(virtualFile, false);
    assertNotNull(fileEditors);
    assertEquals(1, fileEditors.length);
    Editor editor = ((TextEditor)fileEditors[0]).getEditor();
    EditorTestUtil.waitForLoading(editor);
    return editor;
  }

  private static void verifyEditorState(Editor editor, String textWithMarkup) {
    final Document document = new DocumentImpl(textWithMarkup);
    EditorCaretTestUtil.CaretAndSelectionState caretAndSelectionState = EditorTestUtil.extractCaretAndSelectionMarkers(document);
    assertEquals(document.getCharsSequence().toString(), editor.getDocument().getText());
    EditorTestUtil.verifyCaretAndSelectionState(editor, caretAndSelectionState);
  }
}
