/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 hsz Jakub Chrzanowski <jakub@hsz.mobi>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.intellij.openapi.vcs.changes.ignore.lang;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.openapi.vcs.changes.ignore.lexer.IgnoreLexerAdapter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Syntax highlighter definition for {@link IgnoreLanguage}.
 */
@ApiStatus.Internal
public class IgnoreLanguageHighlighter extends SyntaxHighlighterBase {
  @Nullable
  private final VirtualFile currentHighlightedFile;

  private static final Map<IElementType, TextAttributesKey> ATTRIBUTES = new HashMap<>();

  /* Binds parser definitions with highlighter colors. */
  static {
    fillMap(ATTRIBUTES, IgnoreParserDefinition.Lazy.COMMENTS, DefaultLanguageHighlighterColors.LINE_COMMENT);
    fillMap(ATTRIBUTES, IgnoreParserDefinition.Lazy.SECTIONS, DefaultLanguageHighlighterColors.LINE_COMMENT);
    fillMap(ATTRIBUTES, IgnoreParserDefinition.Lazy.HEADERS, DefaultLanguageHighlighterColors.LINE_COMMENT);
  }

  public IgnoreLanguageHighlighter(@Nullable VirtualFile currentHighlightedFile) {
    this.currentHighlightedFile = currentHighlightedFile;
  }

  @NotNull
  @Override
  public Lexer getHighlightingLexer() {
    return new IgnoreLexerAdapter(currentHighlightedFile);
  }

  @Override
  public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
    return pack(ATTRIBUTES.get(tokenType));
  }
}
