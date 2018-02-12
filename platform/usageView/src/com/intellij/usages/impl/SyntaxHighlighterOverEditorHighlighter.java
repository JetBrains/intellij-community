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
package com.intellij.usages.impl;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.util.LayeredHighlighterIterator;
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.search.LexerEditorHighlighterLexer;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class SyntaxHighlighterOverEditorHighlighter implements SyntaxHighlighter {
  private final Lexer lexer;
  private LayeredHighlighterIterator layeredHighlighterIterator;
  private final SyntaxHighlighter highlighter;

  public SyntaxHighlighterOverEditorHighlighter(SyntaxHighlighter _highlighter, VirtualFile file, Project project) {
    if (file.getFileType() == PlainTextFileType.INSTANCE) { // optimization for large files, PlainTextSyntaxHighlighterFactory is slow
      highlighter = new PlainSyntaxHighlighter();
      lexer = highlighter.getHighlightingLexer();
    } else {
      highlighter = _highlighter;
      EditorHighlighter editorHighlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, file);
      
      if (editorHighlighter instanceof LayeredLexerEditorHighlighter) {
        lexer = new LexerEditorHighlighterLexer(editorHighlighter, false);
      }
      else {
        lexer = highlighter.getHighlightingLexer();
      }
    }
  }

  @NotNull
  @Override
  public Lexer getHighlightingLexer() {
    return lexer;
  }

  @NotNull
  @Override
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    final SyntaxHighlighter activeSyntaxHighlighter =
      layeredHighlighterIterator != null ? layeredHighlighterIterator.getActiveSyntaxHighlighter() : highlighter;
    return activeSyntaxHighlighter.getTokenHighlights(tokenType);
  }

  public void restart(@NotNull CharSequence text) {
    lexer.start(text);

    if (lexer instanceof LexerEditorHighlighterLexer) {
      HighlighterIterator iterator = ((LexerEditorHighlighterLexer)lexer).getHighlighterIterator();
      if (iterator instanceof LayeredHighlighterIterator) {
        layeredHighlighterIterator = (LayeredHighlighterIterator)iterator;
      } else {
        layeredHighlighterIterator = null;
      }
    }
  }

  public void resetPosition(int startOffset) {
    if (lexer instanceof LexerEditorHighlighterLexer) {
      ((LexerEditorHighlighterLexer)lexer).resetPosition(startOffset);

      HighlighterIterator iterator = ((LexerEditorHighlighterLexer)lexer).getHighlighterIterator();
      if (iterator instanceof LayeredHighlighterIterator) {
        layeredHighlighterIterator = (LayeredHighlighterIterator)iterator;
      } else {
        layeredHighlighterIterator = null;
      }
    } else {
      CharSequence text = lexer.getBufferSequence();
      lexer.start(text, startOffset, text.length());
    }
  }
}
