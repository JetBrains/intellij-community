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
           || tokenType == GroovyTokenTypes.mGSTRING_BEGIN
           || tokenType == GroovyTokenTypes.mREGEX_BEGIN
           || tokenType == GroovyTokenTypes.mDOLLAR_SLASH_REGEX_BEGIN
           || TokenSets.COMMENT_SET.contains(tokenType)
           || tokenType.getLanguage() != GroovyLanguage.INSTANCE;
  }

  @Override
  public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
    return openingBraceOffset;
  }
}