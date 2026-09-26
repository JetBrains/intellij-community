// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.openapi.editor.impl.EditorTextFieldRendererDocument;

public class EditorTextFieldDocumentTest extends AbstractEditorTest {
  public void testSelection() {
    EditorTextFieldRendererDocument document = new EditorTextFieldRendererDocument();
    document.setText("12345\n67890\n\r12345\r11111");
    assertEquals(5, document.getLineEndOffset(0));
    assertEquals(6, document.getLineStartOffset(1));
    assertEquals(11, document.getLineEndOffset(1));
    assertEquals(12, document.getLineStartOffset(2));
    assertEquals(12, document.getLineEndOffset(2));
    assertEquals(13, document.getLineStartOffset(3));
    assertEquals(18, document.getLineEndOffset(3));
  }
}
