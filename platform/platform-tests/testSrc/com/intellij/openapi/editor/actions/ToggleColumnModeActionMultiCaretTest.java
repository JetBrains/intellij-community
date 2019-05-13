/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

public class ToggleColumnModeActionMultiCaretTest extends LightPlatformCodeInsightFixtureTestCase {
  public void testSingleCaretWithoutSelection() {
    doTestToggleOnOff("line1\n" +
                      "line<caret>2",

                      "line1\n" +
                      "line<caret>2",

                      "line1\n" +
                      "line<caret>2"
    );
  }

  public void testSingleCaretWithForwardSelection() {
    doTestToggleOnOff("l<selection>ine1\n" +
                      "line<caret></selection>2",

                      "l<selection>ine<caret></selection>1\n" +
                      "l<selection>ine<caret></selection>2",

                      "l<selection>ine1\n" +
                      "line<caret></selection>2"
    );
  }

  public void testSingleCaretWithBackwardSelection() {
    doTestToggleOnOff("l<selection><caret>ine1\n" +
                      "line</selection>2",

                      "l<selection><caret>ine</selection>1\n" +
                      "l<selection><caret>ine</selection>2",

                      "l<selection><caret>ine1\n" +
                      "line</selection>2"
    );
  }

  public void testSingleCaretWithCrossSelection() {
    doTestToggleOnOff("line<selection><caret>1\n" +
                      "l</selection>ine2",

                      "l<selection>ine<caret></selection>1\n" +
                      "l<selection>ine<caret></selection>2",

                      "line<selection><caret>1\n" +
                      "l</selection>ine2"
    );
  }

  public void testSingleCaretWithCrossSelection2() {
    doTestToggleOnOff("line<selection>1\n" +
                      "l<caret></selection>ine2",

                      "l<selection><caret>ine</selection>1\n" +
                      "l<selection><caret>ine</selection>2",

                      "line<selection>1\n" +
                      "l<caret></selection>ine2"
    );
  }

  public void testMultipleCarets() {
    doTestToggleOnOff("<caret>l<selection>ine1\n" +
                      "line<caret></selection>2",

                      "l<selection>ine<caret></selection>1\n" +
                      "l<selection>ine<caret></selection>2",

                      "l<selection>ine1\n" +
                      "line<caret></selection>2"
    );
  }

  private void doTestToggleOnOff(String initialState, String afterToggleOn, String afterToggleOff) {
    myFixture.configureByText(FileTypes.PLAIN_TEXT, initialState);
    myFixture.performEditorAction("EditorToggleColumnMode");
    myFixture.checkResult(afterToggleOn);
    myFixture.performEditorAction("EditorToggleColumnMode");
    myFixture.checkResult(afterToggleOff);
  }
}
