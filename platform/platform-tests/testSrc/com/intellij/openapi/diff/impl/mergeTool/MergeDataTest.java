package com.intellij.openapi.diff.impl.mergeTool;

import com.intellij.openapi.diff.BaseDiffTestCase;
import com.intellij.openapi.diff.ActionButtonPresentation;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.testFramework.TempFiles;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

public class MergeDataTest extends BaseDiffTestCase {
  private TempFiles myTempFiles;

  public void testWorkingDocument() throws IOException {
    VirtualFile file = myTempFiles.createVFile("merge", ".txt");
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

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myTempFiles = new TempFiles(myFilesToDelete);
  }

  @Override
  protected void tearDown() throws Exception {
    myTempFiles.deleteAll();
    myTempFiles = null;
    super.tearDown();
  }
}
