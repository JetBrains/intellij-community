// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.wrap;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractWrapTest extends AbstractEditorTest {

  protected CodeStyleSettings mySettings;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    mySettings = CodeStyle.createTestSettings(CodeStyle.getSettings(getProject()));
    mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true;

    CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(mySettings);
  }

  protected void checkWrapOnTyping(@NotNull FileType fileType,
                                   @NotNull String textToType,
                                   @NotNull String initial,
                                   @NotNull String expected) {
    String name = "test." + fileType.getDefaultExtension();
    configureFromFileText(name, initial);
    assertFileTypeResolved(fileType, name);
    for (char c : textToType.toCharArray()) {
      type(c);
    }
    checkResultByText(expected);
  }
}
