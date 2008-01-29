/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

import com.intellij.lexer.LexerBase;
import com.intellij.lexer.MergingLexerAdapter;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.text.CharArrayUtil;

import java.io.IOException;

/**
 * @author ilyas
 */
public class GroovyDocLexer extends MergingLexerAdapter implements GroovyDocTokenTypes {

  private static TokenSet TOKENS_TO_MERGE = TokenSet.create(
      mGDOC_COMMENT_DATA,
      mGDOC_WHITESPACE
  );

  public GroovyDocLexer() {
    super(new AsteriskStripperLexer(new _GroovyDocLexer()),
        TOKENS_TO_MERGE);
  }


  private static class AsteriskStripperLexer extends LexerBase {
    private _GroovyDocLexer myFlexLexer;
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

    public final void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
      myBuffer = buffer;
      myBufferIndex = startOffset;
      myBufferEndOffset = endOffset;
      myTokenType = null;
      myTokenEndOffset = startOffset;
      myFlexLexer.reset(myBuffer, startOffset, endOffset, initialState);
    }

    public final void start(char[] buffer, int startOffset, int endOffset, int initialState) {
      start(buffer, startOffset, endOffset);
    }

    public int getState() {
      return myState;
    }

    public char[] getBuffer() {
      return CharArrayUtil.fromSequence(myBuffer);
    }

    public CharSequence getBufferSequence() {
      return myBuffer;
    }

    public int getBufferEnd() {
      return myBufferEndOffset;
    }

    public final IElementType getTokenType() {
      locateToken();
      return myTokenType;
    }

    public final int getTokenStart() {
      locateToken();
      return myBufferIndex;
    }

    public final int getTokenEnd() {
      locateToken();
      return myTokenEndOffset;
    }


    public final void advance() {
      locateToken();
      myTokenType = null;
    }

    protected final void locateToken() {
      if (myTokenType != null) return;
      _locateToken();

      if (myTokenType == mGDOC_WHITESPACE) {
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
          myTokenType = mGDOC_ASTERISKS;
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
              ? mGDOC_WHITESPACE
              : mGDOC_COMMENT_DATA;

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