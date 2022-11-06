// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

public class SelectSingleLineAtCaretActionTest extends LightPlatformCodeInsightTestCase {
  public void test() {
    prepare("""
              first line
              second<caret> line
              third line""");
    selectSingleLine();
    checkResultByText("""
                        first line
                        <selection><caret>second line
                        </selection>third line"""
    );
  }

  public void testWithSoftWraps() {
    prepare("first line\n" +
            "second line<caret>\n" + // this line will be wrapped and caret is positioned after the wrap
            "third line");
    assertTrue("Failed to activate soft wrapping", EditorTestUtil.configureSoftWraps(getEditor(), 6));
    selectSingleLine();
    checkResultByText("""
                        first line
                        <selection><caret>second line
                        </selection>third line"""
    );
  }

  public void testWithExistingSelection() {
    prepare("""
              first line
              secon<caret><selection>d line
              third li</selection>ne
              fourth line
              fifth line""");

    selectSingleLine();
    checkResultByText("""
                        first line
                        <selection><caret>second line
                        </selection>third line
                        fourth line
                        fifth line""");
    selectSingleLine();
    checkResultByText("""
                        first line
                        <selection><caret>second line
                        </selection>third line
                        fourth line
                        fifth line""");
  }

  public void testWithExistingSelectionWithCaretInTheMiddle() {
    prepare("""
              first line
              <selection>second line
              third <caret>line
              </selection>fourth line
              fifth line""");

    selectSingleLine();
    checkResultByText("""
                        first line
                        second line
                        <selection><caret>third line
                        </selection>fourth line
                        fifth line""");
  }

  private void selectSingleLine() {
    executeAction("EditorSelectSingleLineAtCaret");
  }

  private void prepare(String documentContents) {
    configureFromFileText(getTestName(false) + ".txt", documentContents);
  }
}
