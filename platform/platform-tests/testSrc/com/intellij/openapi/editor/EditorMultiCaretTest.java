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

import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.TestFileType;

public class EditorMultiCaretTest extends AbstractEditorTest {
  private boolean myStoredVirtualSpaceSetting;

  public void setUp() throws Exception {
    super.setUp();
    EditorTestUtil.enableMultipleCarets();
    myStoredVirtualSpaceSetting = EditorSettingsExternalizable.getInstance().isVirtualSpace();
    EditorSettingsExternalizable.getInstance().setVirtualSpace(false);
  }

  public void tearDown() throws Exception {
    EditorSettingsExternalizable.getInstance().setVirtualSpace(myStoredVirtualSpaceSetting);
    EditorTestUtil.disableMultipleCarets();
    super.tearDown();
  }

  public void testCaretAddingAndRemoval() throws Exception {
    init("some <selection>t<caret>ext</selection>\n" +
         "another line",
         TestFileType.TEXT);

    mouse().alt().shift().clickAt(1,1); // alt-shift-click in a 'free space'
    checkResultByText("some <selection>t<caret>ext</selection>\n" +
                      "a<caret>nother line");

    mouse().alt().shift().clickAt(0, 8); // alt-shift-click in existing selection
    checkResultByText("some <selection>t<caret>ext</selection>\n" +
                      "a<caret>nother line");

    mouse().alt().shift().clickAt(0, 6); // alt-shift-click at existing caret with selection
    checkResultByText("some text\n" +
                      "a<caret>nother line");

    mouse().alt().shift().clickAt(1, 1); // alt-shift-click at the sole caret
    checkResultByText("some text\n" +
                      "a<caret>nother line");

    mouse().alt().shift().clickAt(0, 30); // alt-shift-click in virtual space
    checkResultByText("some text<caret>\n" +
                      "a<caret>nother line");

    mouse().clickAt(0, 0); // plain mouse click
    checkResultByText("<caret>some text\n" +
                      "another line");
  }

  public void testAltDragStartingFromWithinLine() throws Exception {
    init("<caret>line\n" +
         "long line\n" +
         "very long line\n" +
         "long line\n" +
         "line",
         TestFileType.TEXT);
    EditorTestUtil.setEditorVisibleSize(myEditor, 1000, 1000);

    mouse().alt().pressAt(1, 6);
    checkResultByText("line\n" +
                      "long l<caret>ine\n" +
                      "very long line\n" +
                      "long line\n" +
                      "line");

    mouse().alt().dragTo(4, 6);
    checkResultByText("line\n" +
                      "long l<caret>ine\n" +
                      "very l<caret>ong line\n" +
                      "long l<caret>ine\n" +
                      "line<caret>");

    mouse().alt().dragTo(4, 8);
    checkResultByText("line\n" +
                      "long l<selection>in<caret></selection>e\n" +
                      "very l<selection>on<caret></selection>g line\n" +
                      "long l<selection>in<caret></selection>e\n" +
                      "line");

    mouse().alt().dragTo(4, 10).release();
    checkResultByText("line\n" +
                      "long l<selection>ine<caret></selection>\n" +
                      "very l<selection>ong <caret></selection>line\n" +
                      "long l<selection>ine<caret></selection>\n" +
                      "line");
  }

  public void testMiddleButtonDragStartingFromVirtualSpace() throws Exception {
    init("<caret>line\n" +
         "long line\n" +
         "very long line\n" +
         "long line\n" +
         "line",
         TestFileType.TEXT);
    EditorTestUtil.setEditorVisibleSize(myEditor, 1000, 1000);

    mouse().middle().pressAt(1, 17);
    checkResultByText("line\n" +
                      "long line<caret>\n" +
                      "very long line\n" +
                      "long line\n" +
                      "line");

    mouse().middle().dragTo(2, 16);
    checkResultByText("line\n" +
                      "long line<caret>\n" +
                      "very long line<caret>\n" +
                      "long line\n" +
                      "line");

    mouse().middle().dragTo(3, 12);
    checkResultByText("line\n" +
                      "long line\n" +
                      "very long li<selection><caret>ne</selection>\n" +
                      "long line\n" +
                      "line");

    mouse().middle().dragTo(3, 6).release();
    checkResultByText("line\n" +
                      "long l<selection><caret>ine</selection>\n" +
                      "very l<selection><caret>ong line</selection>\n" +
                      "long l<selection><caret>ine</selection>\n" +
                      "line");
  }

  public void testTyping() throws Exception {
    init("some<caret> text<caret>\n" +
         "some <selection><caret>other</selection> <selection>text<caret></selection>\n" +
         "<selection>ano<caret>ther</selection> line",
         TestFileType.TEXT
    );
    type('A');
    checkResultByText("someA<caret> textA<caret>\n" +
                      "some A<caret> A<caret>\n" +
                      "A<caret> line");
  }

  public void testCopyPaste() throws Exception {
    init("<selection><caret>one</selection> two \n" +
         "<selection><caret>three</selection> four ",
         TestFileType.TEXT);
    executeAction("EditorCopy");
    executeAction("EditorLineEnd");
    executeAction("EditorPaste");
    checkResultByText("one twoone<caret> \n" +
                      "three fourthree<caret> ");
  }

  public void testCutAndPaste() throws Exception {
    init("<selection>one<caret></selection> two \n" +
         "<selection>three<caret></selection> four ",
         TestFileType.TEXT
    );
    executeAction("EditorCut");
    executeAction("EditorLineEnd");
    executeAction("EditorPaste");
    checkResultByText(" twoone<caret> \n" +
                      " fourthree<caret> ");
  }

  public void testPasteSingleItem() throws Exception {
    init("<selection>one<caret></selection> two \n" +
         "three four ",
         TestFileType.TEXT);
    executeAction("EditorCopy");
    executeAction("EditorCloneCaretBelow");
    executeAction("EditorLineEnd");
    executeAction("EditorPaste");
    checkResultByText("one twoone<caret> \n" +
                      "three fourone<caret> ");
  }

  public void testCutAndPasteMultiline() throws Exception {
    init("one <selection>two \n" +
         "three<caret></selection> four \n" +
         "five <selection>six \n" +
         "seven<caret></selection> eight",
         TestFileType.TEXT);
    executeAction("EditorCut");
    executeAction("EditorLineEnd");
    executeAction("EditorPaste");
    checkResultByText("one  fourtwo \n" +
                      "three<caret> \n" +
                      "five  eightsix \n" +
                      "seven<caret>");
  }
}
