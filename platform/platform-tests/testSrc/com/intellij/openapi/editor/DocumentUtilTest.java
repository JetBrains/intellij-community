// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.util.DocumentUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DocumentUtilTest {
  @Test
  public void testGetIndent() {
    final Document doc = new DocumentImpl("""
                                            no indent
                                             \t some indent
                                            
                                            \s\s\s
                                            \s
                                            """);
    assertEquals("no indent",
                 "", DocumentUtil.getIndent(doc, doc.getLineStartOffset(0) + 2).toString());
    assertEquals("some indent",
                 " \t ", DocumentUtil.getIndent(doc, doc.getLineStartOffset(1) + 2).toString());
    assertEquals("empty line",
                 "", DocumentUtil.getIndent(doc, doc.getLineStartOffset(2)).toString());
    assertEquals("line with spaces",
                 "   ", DocumentUtil.getIndent(doc, doc.getLineStartOffset(3) + 1).toString());
    assertEquals("line with a single space",
                 " ", DocumentUtil.getIndent(doc, doc.getLineStartOffset(4)).toString());
    assertEquals("end of the document",
                 "", DocumentUtil.getIndent(doc, doc.getLineStartOffset(5)).toString());
  }

  @Test
  public void calculateOffsetIsZeroBased() {
    final Document doc = new DocumentImpl("""
                                            line1
                                            line2
                                            """);

    int offset = DocumentUtil.calculateOffset(doc, 0, 0, 0);
    assertEquals(0, offset);
  }
}
