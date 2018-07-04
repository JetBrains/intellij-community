// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.mergeTool;

import com.intellij.openapi.diff.ActionButtonPresentation;
import com.intellij.openapi.diff.BaseDiffTestCase;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;

public class MergeDataTest extends BaseDiffTestCase {
  public void testWorkingDocument() {
    VirtualFile file = getTempDir().createVFile("merge", ".txt");
    assertNotNull(file);
    assertEquals("txt", file.getExtension());
    Document document = FileDocumentManager.getInstance().getDocument(file);
    replaceString(document, 0, document.getTextLength(), "current");
    assertEquals("current", document.getText());
    MergeVersion.MergeDocumentVersion base = new MergeVersion.MergeDocumentVersion(document, "original");
    MergeRequestImpl mergeData =
      new MergeRequestImpl("left", base, "right", myProject, ActionButtonPresentation.APPLY, ActionButtonPresentation.CANCEL_WITH_PROMPT);
    assertEquals("left", mergeData.getContents()[0].getDocument().getText());
    Document workingDocument = mergeData.getContents()[1].getDocument();
    assertEquals("original", workingDocument.getText());
    assertEquals("right", mergeData.getContents()[2].getDocument().getText());
    replaceString(workingDocument, 0, workingDocument.getTextLength(), "corrected");
    mergeData.setResult(DialogWrapper.OK_EXIT_CODE);
    assertEquals("corrected", document.getText());
  }
}
