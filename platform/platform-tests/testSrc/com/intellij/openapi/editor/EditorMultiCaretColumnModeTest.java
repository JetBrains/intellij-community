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
package com.intellij.openapi.editor;

import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

import java.io.IOException;

public class EditorMultiCaretColumnModeTest extends LightPlatformCodeInsightTestCase {
  public void setUp() throws Exception {
    super.setUp();
    EditorTestUtil.enableMultipleCarets();
  }

  public void tearDown() throws Exception {
    EditorTestUtil.disableMultipleCarets();
    super.tearDown();
  }

  public void testUpDown() throws Exception {
    init("line1\n" +
         "li<caret>ne2\n" +
         "line3");

    executeAction("EditorDownWithSelection");
    checkResultByText("line1\n" +
                      "li<caret>ne2\n" +
                      "li<caret>ne3");

    executeAction("EditorDownWithSelection"); // hitting document bottom
    checkResultByText("line1\n" +
                      "li<caret>ne2\n" +
                      "li<caret>ne3");

    executeAction("EditorUpWithSelection");
    checkResultByText("line1\n" +
                      "li<caret>ne2\n" +
                      "line3");

    executeAction("EditorUpWithSelection"); // hitting document top
    checkResultByText("li<caret>ne1\n" +
                      "li<caret>ne2\n" +
                      "line3");

    executeAction("EditorUpWithSelection");
    checkResultByText("li<caret>ne1\n" +
                      "li<caret>ne2\n" +
                      "line3");

    executeAction("EditorDownWithSelection");
    checkResultByText("line1\n" +
                      "li<caret>ne2\n" +
                      "line3");
  }

  public void testPageUpDown() throws Exception {
    init("line1\n" +
         "line2\n" +
         "line3\n" +
         "line4\n" +
         "line<caret>5\n" +
         "line6\n" +
         "line7");
    EditorTestUtil.setEditorVisibleSize(myEditor, 1000, 3);

    executeAction("EditorPageUpWithSelection");
    checkResultByText("line1\n" +
                      "line<caret>2\n" +
                      "line<caret>3\n" +
                      "line<caret>4\n" +
                      "line<caret>5\n" +
                      "line6\n" +
                      "line7");

    executeAction("EditorUpWithSelection");
    checkResultByText("line<caret>1\n" +
                      "line<caret>2\n" +
                      "line<caret>3\n" +
                      "line<caret>4\n" +
                      "line<caret>5\n" +
                      "line6\n" +
                      "line7");

    executeAction("EditorPageDownWithSelection");
    checkResultByText("line1\n" +
                      "line2\n" +
                      "line3\n" +
                      "line<caret>4\n" +
                      "line<caret>5\n" +
                      "line6\n" +
                      "line7");

    executeAction("EditorPageDownWithSelection");
    checkResultByText("line1\n" +
                      "line2\n" +
                      "line3\n" +
                      "line4\n" +
                      "line<caret>5\n" +
                      "line<caret>6\n" +
                      "line<caret>7");

    executeAction("EditorPageUpWithSelection");
    checkResultByText("line1\n" +
                      "line2\n" +
                      "line3\n" +
                      "line<caret>4\n" +
                      "line<caret>5\n" +
                      "line6\n" +
                      "line7");
  }

  public void testSelectionWithKeyboard() throws Exception {
    init("line1\n" +
         "li<caret>ne2\n" +
         "line3");

    executeAction("EditorRightWithSelection");
    checkResultByText("line1\n" +
                      "li<selection>n<caret></selection>e2\n" +
                      "line3");

    executeAction("EditorDownWithSelection");
    checkResultByText("line1\n" +
                      "li<selection>n<caret></selection>e2\n" +
                      "li<selection>n<caret></selection>e3");

    executeAction("EditorLeftWithSelection");
    checkResultByText("line1\n" +
                      "li<caret>ne2\n" +
                      "li<caret>ne3");

    executeAction("EditorLeftWithSelection");
    checkResultByText("line1\n" +
                      "l<selection><caret>i</selection>ne2\n" +
                      "l<selection><caret>i</selection>ne3");

    executeAction("EditorUpWithSelection");
    checkResultByText("line1\n" +
                      "l<selection><caret>i</selection>ne2\n" +
                      "line3");

    executeAction("EditorUpWithSelection");
    checkResultByText("l<selection><caret>i</selection>ne1\n" +
                      "l<selection><caret>i</selection>ne2\n" +
                      "line3");

    executeAction("EditorRightWithSelection");
    checkResultByText("li<caret>ne1\n" +
                      "li<caret>ne2\n" +
                      "line3");

    executeAction("EditorRightWithSelection");
    checkResultByText("li<selection>n<caret></selection>e1\n" +
                      "li<selection>n<caret></selection>e2\n" +
                      "line3");

    executeAction("EditorDownWithSelection");
    checkResultByText("line1\n" +
                      "li<selection>n<caret></selection>e2\n" +
                      "line3");

    executeAction("EditorLeftWithSelection");
    checkResultByText("line1\n" +
                      "li<caret>ne2\n" +
                      "line3");
  }

  public void testSelectNextPrevWord() throws Exception {
    init("aaa aaa<caret>\n" +
         "bbbb bbbb");
    executeAction("EditorDownWithSelection");
    executeAction("EditorPreviousWordWithSelection");
    checkResultByText("aaa <selection><caret>aaa</selection>\n" +
                      "bbbb <selection><caret>bb</selection>bb");
    executeAction("EditorNextWordWithSelection");
    checkResultByText("aaa aaa<caret>\n" +
                      "bbbb bb<selection>bb<caret></selection>");
  }

  private void init(String text) throws IOException {
    configureFromFileText(getTestName(false) + ".txt", text);
    ((EditorEx)myEditor).setColumnMode(true);
  }
}
