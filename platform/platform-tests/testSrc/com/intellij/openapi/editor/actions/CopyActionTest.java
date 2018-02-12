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

import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

import java.awt.datatransfer.DataFlavor;

public class CopyActionTest extends LightPlatformCodeInsightTestCase {
  public void testCopyWithoutSelection() {
    prepare("first line\n" +
            "second<caret> line\n" +
            "third line");
    copy();
    verifyResult("first line\n" +
                 "<caret><selection>second line\n" +
                 "</selection>third line",
                 "second line\n"
    );
  }

  public void testCopyWithoutSelectionWithSoftWraps() {
    prepare("first line\n" +
            "second line<caret>\n" + // this line will be wrapped and caret is positioned after the wrap
            "third line");
    assertTrue("Failed to activate soft wrapping", EditorTestUtil.configureSoftWraps(myEditor, 6));
    copy();
    verifyResult("first line\n" +
                 "<caret><selection>second line\n" +
                 "</selection>third line",
                 "second line\n"
    );
  }

  private void prepare(String documentContents) {
    configureFromFileText(getTestName(false) + ".txt", documentContents);
  }

  private void verifyResult(String expectedDocumentContents, String expectedClipboardContents) {
    checkResultByText(expectedDocumentContents);
    assertEquals("Clipboard contents mismatch",
                 expectedClipboardContents,
                 CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor));
  }
}
