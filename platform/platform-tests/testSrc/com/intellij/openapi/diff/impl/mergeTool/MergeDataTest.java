/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.mergeTool;

import com.intellij.openapi.diff.ActionButtonPresentation;
import com.intellij.openapi.diff.BaseDiffTestCase;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TempFiles;

public class MergeDataTest extends BaseDiffTestCase {
  private TempFiles myTempFiles;

  public void testWorkingDocument() {
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
