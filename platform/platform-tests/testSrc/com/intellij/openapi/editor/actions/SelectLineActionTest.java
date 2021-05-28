/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

public class SelectLineActionTest extends LightPlatformCodeInsightTestCase {
  public void test() {
    prepare("first line\n" +
            "second<caret> line\n" +
            "third line");
    selectLine();
    checkResultByText("first line\n" +
                      "<selection>second line\n" +
                      "</selection><caret>third line"
    );
  }

  public void testWithSoftWraps() {
    prepare("first line\n" +
            "second line<caret>\n" + // this line will be wrapped and caret is positioned after the wrap
            "third line");
    assertTrue("Failed to activate soft wrapping", EditorTestUtil.configureSoftWraps(getEditor(), 6));
    selectLine();
    checkResultByText("first line\n" +
                      "<selection>second line\n" +
                      "</selection><caret>third line"
    );
  }

  public void testWithExistingSelection() {
    prepare("first line\n" +
            "secon<caret><selection>d line\n" +
            "third li</selection>ne\n" +
            "fourth line\n" +
            "fifth line");

    selectLine();
    checkResultByText("first line\n" +
                      "<selection>second line\n" +
                      "third line\n" +
                      "</selection><caret>fourth line\n" +
                      "fifth line"
    );
    selectLine();
    checkResultByText("first line\n" +
                      "<selection>second line\n" +
                      "third line\n" +
                      "fourth line\n" +
                      "</selection><caret>fifth line"
    );
    selectLine();
    checkResultByText("first line\n" +
                      "<selection>second line\n" +
                      "third line\n" +
                      "fourth line\n" +
                      "fifth line</selection><caret>"
    );
  }

  private void prepare(String documentContents) {
    configureFromFileText(getTestName(false) + ".txt", documentContents);
  }
}
