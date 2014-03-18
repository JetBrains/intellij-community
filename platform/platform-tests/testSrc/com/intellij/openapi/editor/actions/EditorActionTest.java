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

import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.testFramework.TestFileType;

public class EditorActionTest extends AbstractEditorTest {
  public void testDownWithSelectionWhenCaretsAreAllowedInsideTabs() throws Exception {
    init("<caret>text",
         TestFileType.TEXT);

    final EditorSettings editorSettings = myEditor.getSettings();
    final boolean old = editorSettings.isCaretInsideTabs();
    try {
      editorSettings.setCaretInsideTabs(true);
      executeAction("EditorDownWithSelection");
      checkResultByText("<selection>text<caret></selection>");
    }
    finally {
      editorSettings.setCaretInsideTabs(old);
    }
  }
  public void testPageDownWithSelectionWhenCaretsAreAllowedInsideTabs() throws Exception {
    init("<caret>line 1\n" +
         "line 2",
         TestFileType.TEXT);
    setEditorVisibleSize(100, 100);

    final EditorSettings editorSettings = myEditor.getSettings();
    final boolean old = editorSettings.isCaretInsideTabs();
    try {
      editorSettings.setCaretInsideTabs(true);
      executeAction("EditorPageDownWithSelection");
      checkResultByText("<selection>line 1\n" +
                        "line 2<caret></selection>");
    }
    finally {
      editorSettings.setCaretInsideTabs(old);
    }
  }
}