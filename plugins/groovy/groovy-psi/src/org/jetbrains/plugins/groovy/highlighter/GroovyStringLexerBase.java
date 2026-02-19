// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.highlighter;

import com.intellij.lexer.LexerBase;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class GroovyStringLexerBase extends LexerBase {
  private static final Logger LOG = Logger.getInstance(GroovyStringLexerBase.class);
  private final IElementType myContentElementType;

  private CharSequence myBuffer;
  private int myBufferEnd;

  private int myStart;
  private int myEnd;
  private IElementType myTokenType;

  public GroovyStringLexerBase(IElementType contentElementType) {
    myContentElementType = contentElementType;
  }

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    if (buffer.length() < endOffset) {
      LOG.error("buffer Length: " + buffer.length() + ", endOffset: " + endOffset, new Attachment("",buffer.toString()));
    }

    myBuffer = buffer;
    myEnd = startOffset;
    myBufferEnd = endOffset;
    myTokenType = locateToken();
  }

  private @Nullable IElementType locateToken() {
    if (myEnd >= myBufferEnd) return null;

    myStart = myEnd;
    if (checkForSimpleValidEscape(myStart)) {
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
    else if (checkForInvalidSimpleEscape(myStart)) {
      myEnd = myStart + 2;
      return StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN;
    }

    while (myEnd < myBufferEnd && !checkForSimpleValidEscape(myEnd) && !checkForHexCodeStart(myEnd)) myEnd++;

    return myContentElementType;
  }

  protected abstract boolean checkForSimpleValidEscape(int start);

  protected abstract boolean checkForInvalidSimpleEscape(int start);

  protected abstract boolean checkForHexCodeStart(int start);

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
  public @NotNull CharSequence getBufferSequence() {
    return myBuffer;
  }

  @Override
  public int getBufferEnd() {
    return myBufferEnd;
  }

  protected char charAt(int i) {
    return myBuffer.charAt(i);
  }
}
