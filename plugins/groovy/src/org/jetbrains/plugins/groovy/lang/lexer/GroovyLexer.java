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

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;

/**
 * @author ilyas
 */
public class GroovyLexer extends LookAheadLexer {
  private static final TokenSet tokensToMerge = TokenSet.create(
      mSL_COMMENT,
      mML_COMMENT,
      mREGEX_BEGIN,
      mREGEX_CONTENT,
      mREGEX_END,
      mWS
  );

  public GroovyLexer() {
    super(new MergingLexerAdapter(new GroovyFlexLexer(), tokensToMerge));
  }

  @Override
  protected void lookAhead(Lexer baseLexer) {
    final IElementType type = baseLexer.getTokenType();
    if (type == mDOT || type == kIMPORT || type == kPACKAGE) {
      addToken(type);
      baseLexer.advance();
      while (baseLexer.getTokenType() == mWS) {
        addToken(mWS);
        baseLexer.advance();
      }
      final IElementType token = baseLexer.getTokenType();
      if (token == kDEF || token == kAS || token == kIN) {
        addToken(mIDENT);
        baseLexer.advance();
      } else {
        addToken(token);
        baseLexer.advance();
      }
    } else if (type == mDOLLAR_SLASHY_LITERAL) {
      //todo rewrite groovy literal treatment with normal dollar-slashy strings treatment

      String text = baseLexer.getTokenText();
      assert text.startsWith("$/");
      assert text.endsWith("/$");
      if (text.length() == 4) {
        addToken(mREGEX_LITERAL);
        baseLexer.advance();
        return;
      }

      int start = baseLexer.getTokenStart() + 1;

      GroovyLexer regex = new GroovyLexer();
      regex.start("/" + text.substring(2, text.length() - 2).replace('/', 'a') + "/");
      while (true) {
        IElementType tokenType = regex.getTokenType();
        int tokenEnd = regex.getTokenEnd();
        regex.advance();

        boolean last = regex.getTokenType() == null;
        if (last) {
          addToken(tokenType);
          baseLexer.advance();
          return;
        }

        addToken(start + tokenEnd, tokenType);
      }
    } else {
      super.lookAhead(baseLexer);
    }
  }
}
