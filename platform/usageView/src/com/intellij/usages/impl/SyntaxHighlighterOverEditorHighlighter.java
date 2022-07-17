// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.util.LayeredHighlighterIterator;
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.PlainTextLikeFileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.impl.AbstractFileTypeBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.search.LexerEditorHighlighterLexer;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class SyntaxHighlighterOverEditorHighlighter implements SyntaxHighlighter {
  private final Lexer lexer;
  private LayeredHighlighterIterator layeredHighlighterIterator;
  @NotNull
  private final SyntaxHighlighter highlighter;

  public SyntaxHighlighterOverEditorHighlighter(@NotNull SyntaxHighlighter _highlighter, @NotNull VirtualFile file, @NotNull Project project) {
    FileType type = file.getFileType();
    if (type instanceof PlainTextLikeFileType && !(type instanceof AbstractFileTypeBase)) { // optimization for large files, PlainTextSyntaxHighlighterFactory is slow
      highlighter = new PlainSyntaxHighlighter();
      lexer = highlighter.getHighlightingLexer();
    }
    else {
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

  @Override
  public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
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