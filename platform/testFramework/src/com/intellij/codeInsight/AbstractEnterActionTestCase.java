// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.application.options.CodeStyle;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jetbrains.annotations.NonNls;

public abstract class AbstractEnterActionTestCase extends LightPlatformCodeInsightTestCase {
  private static final String TEST_PATH = "/codeInsight/enterAction/";

  protected void doGetIndentTest(final PsiFile file, final int lineNum, final String expected) {
    final int offset = PsiDocumentManager.getInstance(getProject()).getDocument(file).getLineEndOffset(lineNum);
    final String actial = CodeStyleManager.getInstance(getProject()).getLineIndent(file, offset);
    assertEquals(expected, actial);
  }

  protected void doTextTest(@NonNls String ext, @NonNls String before, @NonNls String after) {
    configureFromFileText("a." + ext, before);
    performAction();
    checkResultByText(null, after, false);
  }

  protected void doTest(final String ext) throws Exception {
    final String name = getTestName(false);

    configureByFile(TEST_PATH + name + "." + ext);
    performAction();
    checkResultByFile(null, TEST_PATH + name + "_after." + ext, false);
  }

  protected void performAction() {
    type('\n');
  }

  protected CodeStyleSettings getCodeStyleSettings() {
    return CodeStyle.getSettings(getProject());
  }
}
