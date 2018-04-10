// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.impl.AbstractEditorTest;

public class EditorTabOutTest extends AbstractEditorTest {
  public void testMethodCall() {
    doTest("class C { void m() { System.getenv(\"a\"<caret>) } }", 1);
  }

  public void testStringLiteral() {
    doTest("class C { void m() { String s = \"a<caret>\" } }", 1);
  }

  private void doTest(String fileText, int expectedCaretShift) {
    boolean savedSetting = CodeInsightSettings.getInstance().TAB_EXITS_BRACKETS_AND_QUOTES;
    CodeInsightSettings.getInstance().TAB_EXITS_BRACKETS_AND_QUOTES = true;
    try {
      configureFromFileText(getTestName(false) + ".java", fileText);
      int originalCaretOffset = myEditor.getCaretModel().getOffset();
      executeAction(IdeActions.ACTION_BRACE_OR_QUOTE_OUT);
      assertEquals("Unexpected caret offset", originalCaretOffset + expectedCaretShift, myEditor.getCaretModel().getOffset());
    }
    finally {
      CodeInsightSettings.getInstance().TAB_EXITS_BRACKETS_AND_QUOTES = savedSetting;
    }
  }
}
