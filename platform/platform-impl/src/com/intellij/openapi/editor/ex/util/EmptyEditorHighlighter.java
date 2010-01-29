/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.editor.ex.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterClient;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.tree.IElementType;

public class EmptyEditorHighlighter implements EditorHighlighter, PrioritizedDocumentListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter");

  private TextAttributes myAttributes;
  private int myTextLength = 0;
  private HighlighterClient myEditor;

  public EmptyEditorHighlighter(TextAttributes attributes) {
    myAttributes = attributes;
  }

  public void setAttributes(TextAttributes attributes) {
    myAttributes = attributes;
  }

  public void setText(CharSequence text) {
    myTextLength = text.length();
  }

  public void setEditor(HighlighterClient editor) {
    LOG.assertTrue(myEditor == null, "Highlighters cannot be reused with different editors");
    myEditor = editor;
  }

  public void setColorScheme(EditorColorsScheme scheme) {
    setAttributes(scheme.getAttributes(HighlighterColors.TEXT));
  }

  public void documentChanged(DocumentEvent e) {
    myTextLength += e.getNewLength() - e.getOldLength();
  }

  public void beforeDocumentChange(DocumentEvent event) {}

  public int getPriority() {
    return 2;
  }

  public HighlighterIterator createIterator(int startOffset) {
    return new HighlighterIterator(){
      private int index = 0;

      public TextAttributes getTextAttributes() {
        return myAttributes;
      }

      public int getStart() {
        return 0;
      }

      public int getEnd() {
        return myTextLength;
      }

      public void advance() {
        index++;
      }

      public void retreat(){
        index--;
      }

      public boolean atEnd() {
        return index != 0;
      }

      public Document getDocument() {
        return myEditor.getDocument();
      }

      public IElementType getTokenType(){
        return IElementType.find(IElementType.FIRST_TOKEN_INDEX);
      }
    };
  }
}