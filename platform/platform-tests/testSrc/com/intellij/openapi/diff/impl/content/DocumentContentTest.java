package com.intellij.openapi.diff.impl.content;

import com.intellij.openapi.diff.BaseDiffTestCase;
import com.intellij.openapi.diff.DocumentContent;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.diff.impl.incrementalMerge.MergeTestUtils;
import com.intellij.openapi.editor.Document;

public class DocumentContentTest extends BaseDiffTestCase {
  public void testInitialSync() {
    Document baseDocument = MergeTestUtils.createDocument("123");
    DocumentContent content = new DocumentContent(myProject, baseDocument);
    content.addListener(SHOULD_NOT_INVALIDATE);
    Document workingDocument = content.getDocument();
    replaceString(baseDocument, 0, 3, "xyz");
    //assertEquals("123", workingDocument.getText());
    DiffPanelImpl diffPanel = createDiffPanel(null, myProject, false);
    diffPanel.setContents(content, new SimpleContent("1"));
    assertEquals("xyz", workingDocument.getText());
    replaceString(baseDocument, 1, 2, "Y");
    assertEquals("xYz", workingDocument.getText());
    replaceString(baseDocument, 0, 3, "");
    assertEquals(0, workingDocument.getTextLength());
    replaceString(workingDocument, 0, 0, "123");
    assertEquals("123", baseDocument.getText());
  }
}
