// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.wrap;

import com.intellij.openapi.fileTypes.PlainTextFileType;

public class PlainTextWrapTest extends AbstractWrapTest {
  public void testWrapAfterSpaceOnMargin() {
    mySettings.setDefaultRightMargin(10);
    checkWrapOnTyping(PlainTextFileType.INSTANCE, "t", "text text <caret>", "text text \nt<caret>");
  }
}
