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

package org.jetbrains.plugins.groovy.lang.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.lexer.LookAheadLexer;
import com.intellij.lexer.MergingLexerAdapter;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

import static com.intellij.psi.TokenType.WHITE_SPACE;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;

/**
 * @author ilyas
 */
public class GroovyLexer extends LookAheadLexer {
  private static final TokenSet tokensToMerge = TokenSet.create(
    mSL_COMMENT,
    mML_COMMENT,
    mREGEX_CONTENT,
    mDOLLAR_SLASH_REGEX_CONTENT,
    WHITE_SPACE,
    mGSTRING_CONTENT,
    mDOLLAR_SLASH_REGEX_CONTENT,
    mREGEX_CONTENT
  );

  public GroovyLexer() {
    super(new MergingLexerAdapter(new GroovyFlexLexer(), tokensToMerge));
  }

  @Override
  protected void lookAhead(Lexer baseLexer) {
    final IElementType type = baseLexer.getTokenType();
    if (TokenSets.DOTS.contains(type) || type == kIMPORT || type == kPACKAGE) {
      addToken(type);
      baseLexer.advance();
      while (baseLexer.getTokenType() == WHITE_SPACE) {
        addToken(WHITE_SPACE);
        baseLexer.advance();
      }
      if (TokenSets.DOTS.contains(type) && baseLexer.getTokenType()==mAT) {
        addToken(mAT);
        baseLexer.advance();
        while (baseLexer.getTokenType() == WHITE_SPACE) {
          addToken(WHITE_SPACE);
          baseLexer.advance();
        }
      }
      final IElementType token = baseLexer.getTokenType();
      if (shouldBeIdent(type, token)) {
        addToken(mIDENT);
        baseLexer.advance();
      }
      else {
        addToken(token);
        baseLexer.advance();
      }
    }
    else {
      super.lookAhead(baseLexer);
    }
  }

  private static boolean shouldBeIdent(IElementType type, IElementType token) {
    if (!TokenSets.KEYWORDS.contains(token)) return false;

    if (TokenSets.DOTS.contains(type)) {
      return token != kTHIS && token != kSUPER && token != kNEW;
    }
    else if (type == kIMPORT) {
      return token != kSTATIC;
    }
    else if (type == kPACKAGE) {
      return true;
    }

    return false;
  }
}
