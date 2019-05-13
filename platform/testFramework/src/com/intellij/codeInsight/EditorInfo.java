/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.testFramework.EditorTestUtil;

/**
 * @author cdr
 */
public class EditorInfo {
  private final String newFileText;
  public EditorTestUtil.CaretAndSelectionState caretState;

  public EditorInfo(final String fileText) {
    Document document = EditorFactory.getInstance().createDocument(fileText);
    caretState = EditorTestUtil.extractCaretAndSelectionMarkers(document, false);
    newFileText = document.getText();
  }

  public String getNewFileText() {
    return newFileText;
  }

  public void applyToEditor(Editor editor) {
    EditorTestUtil.setCaretsAndSelection(editor, caretState);
  }
}
