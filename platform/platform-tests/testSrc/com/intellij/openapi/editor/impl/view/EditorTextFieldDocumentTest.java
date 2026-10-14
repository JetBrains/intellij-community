// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.EditorTextFieldRendererDocument;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.junit5.TestApplication;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class EditorTextFieldDocumentTest {
  @Test
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

  @Test
  public void testEmptyText() {
    var document = new EditorTextFieldRendererDocument();
    document.setText("");
    assertEquals(0, document.getLineCount());
    assertEquals(0, document.getLineNumber(0));
    assertEquals(0, document.getLineStartOffset(0));
    assertEquals(0, document.getLineEndOffset(0));
  }

  @Test
  public void testLineMappingMatchesDocumentImpl() {
    for (var text : new String[]{"", "a", "a\n", "\n", "\n\n", "ab\ncd", "ab\ncd\n", "ab\r\ncd\ref\n"}) {
      var expected = new DocumentImpl(StringUtil.convertLineSeparators(text));
      var actual = new EditorTextFieldRendererDocument();
      actual.setText(text);
      assertSameLineMapping(expected, actual, "\"" + StringUtil.escapeStringCharacters(text) + "\"");
    }
  }

  private static void assertSameLineMapping(DocumentEx expected, DocumentEx actual, String label) {
    assertEquals(expected.getText(), actual.getText(), label);
    assertEquals(expected.getLineCount(), actual.getLineCount(), label);
    for (var line = 0; line < expected.getLineCount(); line++) {
      var context = label + ", line " + line;
      assertEquals(expected.getLineStartOffset(line), actual.getLineStartOffset(line), context);
      assertEquals(expected.getLineEndOffset(line), actual.getLineEndOffset(line), context);
      assertEquals(expected.getLineSeparatorLength(line), actual.getLineSeparatorLength(line), context);
    }
    for (var offset = 0; offset <= expected.getTextLength(); offset++) {
      assertEquals(expected.getLineNumber(offset), actual.getLineNumber(offset), label + ", offset " + offset);
    }
    var expectedLines = expected.createLineIterator();
    var actualLines = actual.createLineIterator();
    while (!expectedLines.atEnd()) {
      var context = label + ", iterator line " + expectedLines.getLineNumber();
      assertFalse(actualLines.atEnd(), context);
      assertEquals(expectedLines.getLineNumber(), actualLines.getLineNumber(), context);
      assertEquals(expectedLines.getStart(), actualLines.getStart(), context);
      assertEquals(expectedLines.getEnd(), actualLines.getEnd(), context);
      assertEquals(expectedLines.getSeparatorLength(), actualLines.getSeparatorLength(), context);
      expectedLines.advance();
      actualLines.advance();
    }
    assertTrue(actualLines.atEnd(), label);
  }
}
