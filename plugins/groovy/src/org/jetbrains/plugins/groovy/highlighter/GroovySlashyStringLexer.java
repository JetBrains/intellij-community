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

import com.intellij.lexer.LexerBase;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

/**
 * @author Max Medvedev
 */
public class GroovySlashyStringLexer extends LexerBase {
  private CharSequence myBuffer;
  private int myStart;
  private int myBufferEnd;
  private IElementType myTokenType;
  private int myEnd;


  public GroovySlashyStringLexer() {
  }

  @Override
  public void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myBuffer = buffer;
    myEnd = startOffset;
    myBufferEnd = endOffset;
    myTokenType = locateToken();
  }

  @Nullable
  private IElementType locateToken() {
    if (myEnd >= myBufferEnd) return null;

    myStart = myEnd;
    if (checkForEscape(myStart)) {
      myEnd = myStart + 2;
      return StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN;
    }

    while (myEnd < myBufferEnd && !checkForEscape(myEnd)) myEnd++;
    return GroovyTokenTypes.mREGEX_CONTENT;
  }

  private boolean checkForEscape(int start) {
    return myBuffer.charAt(start) == '\\' && start + 1 < myBufferEnd && myBuffer.charAt(start + 1) == '/';
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
