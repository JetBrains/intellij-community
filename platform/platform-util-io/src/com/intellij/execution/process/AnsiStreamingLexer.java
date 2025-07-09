// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class AnsiStreamingLexer {
  // element types
  static final AnsiElementType TEXT = new AnsiElementType("TEXT");
  static final AnsiElementType SGR = new AnsiElementType("SGR");
  static final AnsiElementType CONTROL = new AnsiElementType("CONTROL");

  static final char ESCAPE = '\u001b';
  static final String CSI = ESCAPE + "[";
  static final char SGR_SUFFIX = 'm';

  // current text buffer
  private String myBuffer = "";

  // current token start offset
  private int myStartOffset = 0;

  // current token end offset
  private int myEndOffset = 0;

  private AnsiElementType myElementType = null;

  /**
   * Appending a {@code text} to the stream
   */
  public void append(@NotNull String text) {
    myBuffer = myEndOffset < myBuffer.length() ? myBuffer.substring(myEndOffset) + text : text;
    myStartOffset = 0;
    myEndOffset = 0;
  }

  /**
   * @return element type of current token. null means we are not started or at the end of stream
   */
  @Nullable
  AnsiElementType getElementType() {
    return myElementType;
  }

  /**
   * @return text of current token of null if we've not started or at the end of stream
   */
  @Nullable
  String getElementText() {
    return myElementType == null ? null : myBuffer.substring(myStartOffset, myEndOffset);
  }

  /**
   * @return for {@link #SGR} elements return only content part of the text, between {@code CSI} and {@code m}. For the rest works
   * the same way as {@link #getElementText()}
   */
  @Nullable
  String getElementTextSmart() {
    if (myElementType == SGR) {
      return myBuffer.substring(myStartOffset + CSI.length(), myEndOffset - 1);
    }
    return getElementText();
  }

  /**
   * Advances lexer till the end or text
   */
  @Nullable
  public String getNextText() {
    do {
      advance();
      if (getElementType() == TEXT) {
        return getElementText();
      }
    }
    while (getElementType() != null);
    return null;
  }

  /**
   * advances lexer to the next token if possible
   */
  void advance() {
    if (myEndOffset == myBuffer.length()) {
      // EOF
      myElementType = null;
      return;
    }
    myStartOffset = myEndOffset;
    if (myBuffer.charAt(myEndOffset) != ESCAPE) {
      // just a text
      advanceToEscape();
      return;
    }
    if (myEndOffset + 1 == myBuffer.length()) {
      incompleteSequence();
      return;
    }

    myEndOffset++;

    if (decodeEscapeSequence()) {
      return;
    }

    advanceToEscape();
  }

  /**
   * Advancing after CSI sequence, see https://en.wikipedia.org/wiki/ANSI_escape_code#CSI_sequences
   *
   * @implSpec {@code The ESC [ is followed by any number (including none) of "parameter bytes" in the
   * range 0x30–0x3F (ASCII 0–9:;<=>?), then by any number of "intermediate bytes" in the range 0x20–0x2F (ASCII space and
   * !"#$%&'()*+,-./), then finally by a single "final byte" in the range 0x40–0x7E (ASCII @A–Z[\]^_`a–z{|}~).}
   */
  private void processCSISequence() {
    while (myEndOffset < myBuffer.length() && isInRange(myBuffer.charAt(myEndOffset), (char)0x30, (char)0x3F)) {
      myEndOffset++;
    }
    while (myEndOffset < myBuffer.length() && isInRange(myBuffer.charAt(myEndOffset), (char)0x20, (char)0x2F)) {
      myEndOffset++;
    }
    if (myEndOffset == myBuffer.length()) {
      incompleteSequence();
      return;
    }

    char lastChar = myBuffer.charAt(myEndOffset);
    myEndOffset++;
    if (lastChar == SGR_SUFFIX) {
      myElementType = SGR;
    }
    else if (0x40 <= lastChar && lastChar <= 0x7E) {
      myElementType = CONTROL;
    }
    else {
      // broken seqeuence, considering as a text
      advanceToEscape();
    }
  }

  private boolean decodeEscapeSequence() {

    // There can by anything from 0x40–0x5F  https://en.wikipedia.org/wiki/ANSI_escape_code#Escape_sequences
    // but we are ignoring most of it for now

    switch (myBuffer.charAt(myEndOffset)) {
      case '[' -> {
        myEndOffset++;
        processCSISequence();
        return true;
      }
      case '=' -> { //DECKPAM
        myElementType = CONTROL;
        myEndOffset++;
        return true;
      }
      default -> {
        return false;
      }
    }
  }

  private void advanceToEscape() {
    int escapeIndex = myBuffer.indexOf(ESCAPE, myEndOffset);
    myEndOffset = escapeIndex != -1 ? escapeIndex : myBuffer.length();
    myElementType = TEXT;
  }

  private void incompleteSequence() {
    myElementType = null;
    myEndOffset = myStartOffset;
  }

  /**
   * @return true iff {@code character} is inside chars range inclusive
   */
  private static boolean isInRange(char character, char startRange, char endRange) {
    return startRange <= character && character <= endRange;
  }

  /**
   * representing element type for ANSI stream. Not using ElementType to avoid wasting tokens
   */
  static final class AnsiElementType {
    private final String myName;

    private AnsiElementType(String name) {
      myName = name;
    }

    @Override
    public String toString() {
      return "ANSI: " + myName;
    }
  }
}
