// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.annotations.NotNull;

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

  @Override
  public void setText(@NotNull CharSequence text) {
    myTextLength = text.length();
  }

  @Override
  public void setEditor(@NotNull HighlighterClient editor) {
    LOG.assertTrue(myEditor == null, "Highlighters cannot be reused with different editors");
    myEditor = editor;
  }

  @Override
  public void setColorScheme(@NotNull EditorColorsScheme scheme) {
    setAttributes(scheme.getAttributes(HighlighterColors.TEXT));
  }

  @Override
  public void documentChanged(@NotNull DocumentEvent e) {
    myTextLength += e.getNewLength() - e.getOldLength();
  }

  @Override
  public int getPriority() {
    return 2;
  }

  @NotNull
  @Override
  public HighlighterIterator createIterator(int startOffset) {
    return new HighlighterIterator(){
      private int index = 0;

      @Override
      public TextAttributes getTextAttributes() {
        return myAttributes;
      }

      @Override
      public int getStart() {
        return 0;
      }

      @Override
      public int getEnd() {
        return myTextLength;
      }

      @Override
      public void advance() {
        index++;
      }

      @Override
      public void retreat(){
        index--;
      }

      @Override
      public boolean atEnd() {
        return index != 0;
      }

      @Override
      public Document getDocument() {
        return myEditor.getDocument();
      }

      @Override
      public IElementType getTokenType(){
        return IElementType.find(IElementType.FIRST_TOKEN_INDEX);
      }
    };
  }
}