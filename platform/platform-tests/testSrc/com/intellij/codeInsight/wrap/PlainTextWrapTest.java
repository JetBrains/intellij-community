// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.wrap;

import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.testFramework.junit5.TestApplication;
import org.junit.jupiter.api.Test;

@TestApplication
public class PlainTextWrapTest extends AbstractWrapTest {
  @Test
  public void testWrapAfterSpaceOnMargin() {
    myFixture.runTest(() -> {
      mySettings.setDefaultRightMargin(10);
      checkWrapOnTyping(PlainTextFileType.INSTANCE, "t", "text text <caret>", "text text \nt<caret>");
    });
  }
}
