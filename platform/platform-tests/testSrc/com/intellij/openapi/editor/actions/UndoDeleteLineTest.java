// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.impl.AbstractEditorTest;

public class UndoDeleteLineTest extends AbstractEditorTest {
  @Override
  protected boolean isRunInCommand() {
    // Otherwise undoing doesn't work
    return false;
  }

  public void test() {
    String before = """
      1
      2
      3<caret>""";

    String after = """
      1
      2<caret>""";

    configureFromFileText(getTestName(false) + ".txt", before);
    deleteLine();
    deleteLine();
    executeAction(IdeActions.ACTION_UNDO);
    checkResultByText(after);
  }
}
