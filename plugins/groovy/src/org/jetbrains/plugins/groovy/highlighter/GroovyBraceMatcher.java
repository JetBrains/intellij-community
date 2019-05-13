// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.highlighter;

import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;

/**
 * @author ilyas
 */
public class GroovyBraceMatcher implements PairedBraceMatcher {

  private static final BracePair[] PAIRS = {
    new BracePair(GroovyTokenTypes.mLPAREN, GroovyTokenTypes.mRPAREN, false),
    new BracePair(GroovyTokenTypes.mLBRACK, GroovyTokenTypes.mRBRACK, false),
    new BracePair(GroovyTokenTypes.mLCURLY, GroovyTokenTypes.mRCURLY, true),

    new BracePair(GroovyDocTokenTypes.mGDOC_INLINE_TAG_START, GroovyDocTokenTypes.mGDOC_INLINE_TAG_END, false),
    new BracePair(GroovyDocTokenTypes.mGDOC_TAG_VALUE_LPAREN, GroovyDocTokenTypes.mGDOC_TAG_VALUE_RPAREN, false),

    new BracePair(GroovyTokenTypes.mGSTRING_BEGIN, GroovyTokenTypes.mGSTRING_END, false),
    new BracePair(GroovyTokenTypes.mREGEX_BEGIN, GroovyTokenTypes.mREGEX_END, false),
    new BracePair(GroovyTokenTypes.mDOLLAR_SLASH_REGEX_BEGIN, GroovyTokenTypes.mDOLLAR_SLASH_REGEX_END, false),
  };

  @NotNull
  @Override
  public BracePair[] getPairs() {
    return PAIRS;
  }

  @Override
  public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType braceType, @Nullable IElementType tokenType) {
    return tokenType == null
           || tokenType == TokenType.WHITE_SPACE
           || tokenType == GroovyTokenTypes.mSEMI
           || tokenType == GroovyTokenTypes.mCOMMA
           || tokenType == GroovyTokenTypes.mRPAREN
           || tokenType == GroovyTokenTypes.mRBRACK
           || tokenType == GroovyTokenTypes.mRCURLY
           || TokenSets.COMMENT_SET.contains(tokenType)
           || tokenType.getLanguage() != GroovyLanguage.INSTANCE;
  }

  @Override
  public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
    return openingBraceOffset;
  }
}