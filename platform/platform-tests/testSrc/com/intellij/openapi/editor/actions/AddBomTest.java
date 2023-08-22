// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

import java.nio.charset.StandardCharsets;

public class AddBomTest extends LightPlatformCodeInsightTestCase {
  public void testMustAddBomForUtf() {
    configureFromFileText("x.txt", "abc");
    assertEquals(StandardCharsets.UTF_8, getVFile().getCharset());
    assertNull(getVFile().getBOM());
    executeAction("AddBom", getEditor(), getProject());
    assertEquals(StandardCharsets.UTF_8, getVFile().getCharset());
    assertOrderedEquals(CharsetToolkit.UTF8_BOM, getVFile().getBOM());
  }
  
  public void testMustBeDisabledForAnsi() {
    configureFromFileText("x.txt", "abc");
    assertEquals(StandardCharsets.UTF_8, getVFile().getCharset());
    assertNull(getVFile().getBOM());
    getVFile().setCharset(StandardCharsets.ISO_8859_1);
    assertThrows(AssertionError.class, () -> executeAction("AddBom", getEditor(), getProject()));
    assertNull(getVFile().getBOM());
  }
  public void testMustBeDisabledForAlreadyBOMed() {
    configureFromFileText("x.txt", "abc");
    assertEquals(StandardCharsets.UTF_8, getVFile().getCharset());
    assertNull(getVFile().getBOM());
    executeAction("AddBom", getEditor(), getProject());
    assertEquals(StandardCharsets.UTF_8, getVFile().getCharset());
    assertOrderedEquals(CharsetToolkit.UTF8_BOM, getVFile().getBOM());

    assertThrows(AssertionError.class, () -> executeAction("AddBom", getEditor(), getProject()));
    assertOrderedEquals(CharsetToolkit.UTF8_BOM, getVFile().getBOM());
  }
}
