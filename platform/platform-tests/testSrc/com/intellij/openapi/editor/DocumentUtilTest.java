// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.util.DocumentUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DocumentUtilTest {
  @Test
  public void testGetIndent() {
    final Document doc = new DocumentImpl("line1\n" +
                                          " \t line2");
    assertEquals(" \t ", DocumentUtil.getIndent(doc, doc.getTextLength() - 1).toString());
  }
}
