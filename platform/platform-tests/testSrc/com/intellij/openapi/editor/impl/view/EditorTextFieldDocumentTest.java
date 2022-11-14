// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.openapi.editor.impl.EditorTextFieldRendererDocument;

public class EditorTextFieldDocumentTest extends AbstractEditorTest {
  public void testSelection() {
    EditorTextFieldRendererDocument document = new EditorTextFieldRendererDocument();
    document.setText("""
                       12345
                       67890
                       """);
    assertEquals(5, document.getLineEndOffset(0));
  }
}
