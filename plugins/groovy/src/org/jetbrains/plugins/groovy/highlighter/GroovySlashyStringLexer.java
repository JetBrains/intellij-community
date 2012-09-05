/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.diagnostic.LogMessageEx;
import com.intellij.lexer.LexerBase;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

/**
 * @author Max Medvedev
 */
public class GroovySlashyStringLexer extends LexerBase {
  private static final Logger LOG = Logger.getInstance(GroovySlashyStringLexer.class);

  private CharSequence myBuffer;
  private int myStart;
  private int myBufferEnd;
  private IElementType myTokenType;
  private int myEnd;


  public GroovySlashyStringLexer() {
  }

  @Override
  public void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
    if (buffer.length()<endOffset) {
      LogMessageEx.error(LOG, "buffer Length: " + buffer.length() + ", endOffset: " + endOffset, buffer.toString());
    }

    myBuffer = buffer;
    myEnd = startOffset;
    myBufferEnd = endOffset;
    myTokenType = locateToken();
  }

  @Nullable
  private IElementType locateToken() {
    if (myEnd >= myBufferEnd) return null;

    myStart = myEnd;
    if (checkForSlashEscape(myStart)) {
      myEnd = myStart + 2;
      return StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN;
    }
    else if (checkForHexCodeStart(myStart)) {
      for (myEnd = myStart + 2; myEnd < myStart + 6; myEnd++) {
        if (myEnd >= myBufferEnd || !StringUtil.isHexDigit(myBuffer.charAt(myEnd))) {
          return StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN;
        }
      }
      return StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN;
    }

    while (myEnd < myBufferEnd && !checkForSlashEscape(myEnd) && !checkForHexCodeStart(myEnd)) myEnd++;
    return GroovyTokenTypes.mREGEX_CONTENT;
  }

  private boolean checkForSlashEscape(int start) {
    return myBuffer.charAt(start) == '\\' && start + 1 < myBufferEnd && myBuffer.charAt(start + 1) == '/';
  }
  
  private boolean checkForHexCodeStart(int start) {
    return myBuffer.charAt(start) == '\\' && start + 1 < myBufferEnd && myBuffer.charAt(start + 1) == 'u';
  }

  @Override
  public int getState() {
    return 0;
  }

  @Override
  public IElementType getTokenType() {
    return myTokenType;
  }

  @Override
  public int getTokenStart() {
    return myStart;
  }

  @Override
  public int getTokenEnd() {
    return myEnd;
  }

  @Override
  public void advance() {
    myTokenType = locateToken();
  }

  @Override
  public CharSequence getBufferSequence() {
    return myBuffer;
  }

  @Override
  public int getBufferEnd() {
    return myBufferEnd;
  }
}
