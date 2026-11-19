// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.autodetect;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("SameParameterValue")
public abstract class AbstractIndentAutoDetectionTest extends LightPlatformCodeInsightTestCase {

  @NotNull
  protected abstract String getFileNameWithExtension();

  @Override
  @NotNull
  protected abstract String getTestDataPath();

  protected void doTestMaxUsedIndent(int indentExpected, int timesUsedExpected) {
    configureByFile(getFileNameWithExtension());
    IndentAutoDetectionTestUtil.checkMaxUsedIndent(getProject(), getFile(), indentExpected, timesUsedExpected);
  }

  protected void doTestMaxUsedIndent(int indentExpected) {
    configureByFile(getFileNameWithExtension());
    IndentAutoDetectionTestUtil.checkMaxUsedIndent(getProject(), getFile(), indentExpected);
  }

  protected void doTestTabsUsed() {
    doTestTabsUsed(null);
  }

  protected void doTestIndentSize(int expectedIndent) {
    configureByFile(getFileNameWithExtension());
    IndentAutoDetectionTestUtil.checkIndentSize(getProject(), getFile(), getEditor().getDocument(), null, expectedIndent);
  }
  
  protected void doTestIndentSizeFromText(String text, @Nullable CommonCodeStyleSettings.IndentOptions options,  int expectedIndent) {
    configureFromFileText(getFileNameWithExtension(), text);
    IndentAutoDetectionTestUtil.checkIndentSize(getProject(), getFile(), getEditor().getDocument(), options, expectedIndent);
  }

  protected void doTestTabsUsed(@Nullable CommonCodeStyleSettings.IndentOptions defaultIndentOptions) {
    configureByFile(getFileNameWithExtension());
    IndentAutoDetectionTestUtil.checkTabsUsed(getProject(), getFile(), getEditor().getDocument(), defaultIndentOptions);
  }

  @NotNull
  public CommonCodeStyleSettings.IndentOptions detectIndentOptions(@NotNull VirtualFile file, @NotNull Document document) {
    return IndentAutoDetectionTestUtil.detectIndentOptions(getProject(), file, document);
  }
}
