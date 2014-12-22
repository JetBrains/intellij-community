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

package org.jetbrains.plugins.groovy.lang.groovydoc.lexer;

import com.intellij.lexer.*;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author ilyas
 */
public class GroovyDocLexer extends MergingLexerAdapter {

  private static final TokenSet TOKENS_TO_MERGE = TokenSet.create(
      GroovyDocTokenTypes.mGDOC_COMMENT_DATA,
      TokenType.WHITE_SPACE
  );

  public GroovyDocLexer() {
    super(new LookAheadLexer(new AsteriskStripperLexer(new _GroovyDocLexer())) {
      @Override
      protected void lookAhead(Lexer baseLexer) {
        if (baseLexer.getTokenType() == GroovyDocTokenTypes.mGDOC_INLINE_TAG_END) {
          advanceAs(baseLexer, GroovyDocTokenTypes.mGDOC_COMMENT_DATA);
          return;
        }
        
        if (baseLexer.getTokenType() == GroovyDocTokenTypes.mGDOC_INLINE_TAG_START) {
          int depth = 0;
          while (true) {
            IElementType type = baseLexer.getTokenType();
            if (type == null) {
              break;
            }
            if (type == GroovyDocTokenTypes.mGDOC_INLINE_TAG_START) {
              depth++;
            }
            advanceLexer(baseLexer);
            if (type == GroovyDocTokenTypes.mGDOC_INLINE_TAG_END) {
              depth--;
            }
            if (depth == 0) {
              break;
            }
          }
          return;
        }
        
        super.lookAhead(baseLexer);
      }
    }, TOKENS_TO_MERGE);
  }

  private static class AsteriskStripperLexer extends LexerBase {
    private final _GroovyDocLexer myFlexLexer;
    private CharSequence myBuffer;
    private int myBufferIndex;
    private int myBufferEndOffset;
    private int myTokenEndOffset;
    private int myState;
    private IElementType myTokenType;
    private boolean myAfterLineBreak;
    private boolean myInLeadingSpace;

    public AsteriskStripperLexer(final _GroovyDocLexer lexer) {
      myFlexLexer = lexer;
    }

    @Override
    public final void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
      myBuffer = buffer;
      myBufferIndex = startOffset;
      myBufferEndOffset = endOffset;
      myTokenType = null;
      myTokenEndOffset = startOffset;
      myFlexLexer.reset(myBuffer, startOffset, endOffset, initialState);
    }

    @Override
    public int getState() {
      return getTokenStart() == 0 ? 0 : myState;
    }

    @Override
    @NotNull
    public CharSequence getBufferSequence() {
      return myBuffer;
    }

    @Override
    public int getBufferEnd() {
      return myBufferEndOffset;
    }

    @Override
    public final IElementType getTokenType() {
      locateToken();
      return myTokenType;
    }

    @Override
    public final int getTokenStart() {
      locateToken();
      return myBufferIndex;
    }

    @Override
    public final int getTokenEnd() {
      locateToken();
      return myTokenEndOffset;
    }


    @Override
    public final void advance() {
      locateToken();
      myTokenType = null;
    }

    protected final void locateToken() {
      if (myTokenType != null) return;
      _locateToken();

      if (myTokenType == TokenType.WHITE_SPACE) {
        myAfterLineBreak = CharArrayUtil.containLineBreaks(myBuffer, getTokenStart(), getTokenEnd());
      }
    }

    private void _locateToken() {
      if (myTokenEndOffset == myBufferEndOffset) {
        myTokenType = null;
        myBufferIndex = myBufferEndOffset;
        return;
      }

      myBufferIndex = myTokenEndOffset;

      if (myAfterLineBreak) {
        myAfterLineBreak = false;
        while (myTokenEndOffset < myBufferEndOffset && myBuffer.charAt(myTokenEndOffset) == '*' &&
            (myTokenEndOffset + 1 >= myBufferEndOffset || myBuffer.charAt(myTokenEndOffset + 1) != '/')) {
          myTokenEndOffset++;
        }

        myInLeadingSpace = true;
        if (myBufferIndex < myTokenEndOffset) {
          myTokenType = GroovyDocTokenTypes.mGDOC_ASTERISKS;
          return;
        }
      }

      if (myInLeadingSpace) {
        myInLeadingSpace = false;
        boolean lf = false;
        while (myTokenEndOffset < myBufferEndOffset && Character.isWhitespace(myBuffer.charAt(myTokenEndOffset))) {
          if (myBuffer.charAt(myTokenEndOffset) == '\n') lf = true;
          myTokenEndOffset++;
        }

        final int state = myFlexLexer.yystate();
        if (state == _GroovyDocLexer.COMMENT_DATA ||
            myTokenEndOffset < myBufferEndOffset && (myBuffer.charAt(myTokenEndOffset) == '@' ||
                myBuffer.charAt(myTokenEndOffset) == '{' ||
                myBuffer.charAt(myTokenEndOffset) == '\"' ||
                myBuffer.charAt(myTokenEndOffset) == '<')) {
          myFlexLexer.yybegin(_GroovyDocLexer.COMMENT_DATA_START);
        }

        if (myBufferIndex < myTokenEndOffset) {
          myTokenType = lf || state == _GroovyDocLexer.PARAM_TAG_SPACE || state == _GroovyDocLexer.TAG_DOC_SPACE || state == _GroovyDocLexer.INLINE_TAG_NAME || state == _GroovyDocLexer.DOC_TAG_VALUE_IN_PAREN
              ? TokenType.WHITE_SPACE
              : GroovyDocTokenTypes.mGDOC_COMMENT_DATA;

          return;
        }
      }

      flexLocateToken();
    }

    private void flexLocateToken() {
      try {
        myState = myFlexLexer.yystate();
        myFlexLexer.goTo(myBufferIndex);
        myTokenType = myFlexLexer.advance();
        myTokenEndOffset = myFlexLexer.getTokenEnd();
      }
      catch (IOException e) {
        // Can't be
      }
    }
  }
}
