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

import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.openapi.vcs.changes.ignore.psi.IgnoreTypes;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Definition of {@link PairedBraceMatcher} class.
 */
public class IgnoreBraceMatcher implements PairedBraceMatcher {
  /**
   * Array of definitions for brace pairs.
   */
  private static final BracePair[] PAIRS = new BracePair[]{
    new BracePair(IgnoreTypes.BRACKET_LEFT, IgnoreTypes.BRACKET_RIGHT, false),
  };

  /**
   * Returns the array of definitions for brace pairs that need to be matched when
   * editing code in the language.
   *
   * @return the array of brace pair definitions.
   */
  @Override
  public BracePair @NotNull [] getPairs() {
    return PAIRS;
  }

  /**
   * Returns true if paired rbrace should be inserted after lbrace of given type when lbrace is encountered before
   * contextType token.
   * It is safe to always return true, then paired brace will be inserted anyway.
   *
   * @param lbraceType  lbrace for which information is queried
   * @param contextType token type that follows lbrace
   * @return true / false as described
   */
  @Override
  public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType,
                                                 @Nullable IElementType contextType) {
    return true;
  }

  /**
   * Returns the start offset of the code construct which owns the opening structural brace at the specified offset.
   * For example, if the opening brace belongs to an 'if' statement, returns the start offset of the 'if' statement.
   *
   * @param file               the file in which brace matching is performed.
   * @param openingBraceOffset the offset of an opening structural brace.
   * @return the offset of corresponding code construct, or the same offset if not defined.
   */
  @Override
  public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
    return openingBraceOffset;
  }
}
