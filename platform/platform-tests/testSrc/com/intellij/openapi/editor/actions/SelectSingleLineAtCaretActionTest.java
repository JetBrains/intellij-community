// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

public class SelectSingleLineAtCaretActionTest extends LightPlatformCodeInsightTestCase {
  public void test() {
    prepare("first line\n" +
            "second<caret> line\n" +
            "third line");
    selectSingleLine();
    checkResultByText("first line\n" +
                      "<selection><caret>second line\n" +
                      "</selection>third line"
    );
  }

  public void testWithSoftWraps() {
    prepare("first line\n" +
            "second line<caret>\n" + // this line will be wrapped and caret is positioned after the wrap
            "third line");
    assertTrue("Failed to activate soft wrapping", EditorTestUtil.configureSoftWraps(getEditor(), 6));
    selectSingleLine();
    checkResultByText("first line\n" +
                      "<selection><caret>second line\n" +
                      "</selection>third line"
    );
  }

  public void testWithExistingSelection() {
    prepare("first line\n" +
            "secon<caret><selection>d line\n" +
            "third li</selection>ne\n" +
            "fourth line\n" +
            "fifth line");

    selectSingleLine();
    checkResultByText("first line\n" +
                      "<selection><caret>second line\n" +
                      "</selection>third line\n" +
                      "fourth line\n" +
                      "fifth line");
    selectSingleLine();
    checkResultByText("first line\n" +
                      "<selection><caret>second line\n" +
                      "</selection>third line\n" +
                      "fourth line\n" +
                      "fifth line");
  }

  public void testWithExistingSelectionWithCaretInTheMiddle() {
    prepare("first line\n" +
            "<selection>second line\n" +
            "third <caret>line\n" +
            "</selection>fourth line\n" +
            "fifth line");

    selectSingleLine();
    checkResultByText("first line\n" +
                      "second line\n" +
                      "<selection><caret>third line\n" +
                      "</selection>fourth line\n" +
                      "fifth line");
  }

  private void selectSingleLine() {
    executeAction("EditorSelectSingleLineAtCaret");
  }

  private void prepare(String documentContents) {
    configureFromFileText(getTestName(false) + ".txt", documentContents);
  }
}
