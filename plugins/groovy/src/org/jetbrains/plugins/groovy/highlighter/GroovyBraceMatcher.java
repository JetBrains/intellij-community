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

package org.jetbrains.plugins.groovy.highlighter;

import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.TokenType.WHITE_SPACE;
import static org.jetbrains.plugins.groovy.GroovyFileType.GROOVY_LANGUAGE;
import static org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes.mGDOC_INLINE_TAG_END;
import static org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes.mGDOC_INLINE_TAG_START;
import static org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes.mGDOC_TAG_VALUE_LPAREN;
import static org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes.mGDOC_TAG_VALUE_RPAREN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;
import static org.jetbrains.plugins.groovy.lang.lexer.TokenSets.COMMENT_SET;

/**
 * @author ilyas
 */
public class GroovyBraceMatcher implements PairedBraceMatcher {

  private static final BracePair[] PAIRS = {
    new BracePair(mLPAREN, mRPAREN, false),
    new BracePair(mLBRACK, mRBRACK, false),
    new BracePair(mLCURLY, mRCURLY, true),

    new BracePair(mGDOC_INLINE_TAG_START, mGDOC_INLINE_TAG_END, false),
    new BracePair(mGDOC_TAG_VALUE_LPAREN, mGDOC_TAG_VALUE_RPAREN, false),

    new BracePair(mGSTRING_BEGIN, mGSTRING_END, false),
    new BracePair(mREGEX_BEGIN, mREGEX_END, false),
    new BracePair(mDOLLAR_SLASH_REGEX_BEGIN, mDOLLAR_SLASH_REGEX_END, false),
  };

  public BracePair[] getPairs() {
    return PAIRS;
  }

  public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType braceType, @Nullable IElementType tokenType) {
    return tokenType == null
           || tokenType == WHITE_SPACE
           || tokenType == mSEMI
           || tokenType == mCOMMA
           || tokenType == mRPAREN
           || tokenType == mRBRACK
           || tokenType == mRCURLY
           || tokenType == mGSTRING_BEGIN
           || tokenType == mREGEX_BEGIN
           || tokenType == mDOLLAR_SLASH_REGEX_BEGIN
           || COMMENT_SET.contains(tokenType)
           || tokenType.getLanguage() != GROOVY_LANGUAGE;
  }

  public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
    return openingBraceOffset;
  }
}