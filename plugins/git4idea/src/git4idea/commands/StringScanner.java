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
package git4idea.commands;

import org.jetbrains.annotations.NotNull;

/**
 * A parser of strings that is oriented to scanning typical git outputs
 */
public class StringScanner {
  /**
   * The text to scan
   */
  private final String myText;
  /**
   * The text position
   */
  private int myPosition;

  /**
   * The constructor from text
   *
   * @param text the text to scan
   */
  public StringScanner(@NotNull final String text) {
    myText = text;
    myPosition = 0;
  }

  /**
   * @return true if there are more data available
   */
  public boolean hasMoreData() {
    return myPosition < myText.length();
  }

  /**
   * @return true if the current position is end of line or end of the file
   */
  public boolean isEol() {
    if (!hasMoreData()) {
      return true;
    }
    final char ch = myText.charAt(myPosition);
    return ch == '\n' || ch == '\r';
  }

  /**
   * Continue to the next line, the rest of the current line is skipped
   */
  public void nextLine() {
    while (!isEol()) {
      myPosition++;
    }
    if (hasMoreData()) {
      final char ch = myText.charAt(myPosition++);
      if (hasMoreData()) {
        final char ch2 = myText.charAt(myPosition);
        if (ch == '\n' && ch2 == '\r' || ch == '\r' && ch2 == '\n') {
          myPosition++;
        }
      }
    }
  }

  /**
   * Gets next token that is ended by space or new line. Consumes space but not a new line.
   * Start position is the current. So if the string starts with space a empty token is returned.
   *
   * @return a token
   */
  public String spaceToken() {
    return boundedToken(' ');
  }

  /**
   * Gets next token that is ended by tab or new line. Consumes tab but not a new line.
   * Start position is the current. So if the string starts with space a empty token is returned.
   *
   * @return a token
   */
  public String tabToken() {
    return boundedToken('\t');
  }

  /**
   * Gets next token that is ended by {@code boundaryChar} or new line. Consumes {@code boundaryChar} but not a new line.
   * Start position is the current. So if the string starts with {@code boundaryChar} a empty token is returned.
   *
   * @param boundaryChar a boundary character
   * @return a token
   */
  public String boundedToken(final char boundaryChar) {
    return boundedToken(boundaryChar, false);
  }

  /**
   * Gets next token that is ended by {@code boundaryChar} or new line. Consumes {@code boundaryChar} but not a new line (if it is not ignored).
   * Start position is the current. So if the string starts with {@code boundaryChar} a empty token is returned.
   *
   * @param boundaryChar a boundary character
   * @param ignoreEol    if true, the end of line is considered as normal character and consumed
   * @return a token
   */
  public String boundedToken(char boundaryChar, boolean ignoreEol) {
    int start = myPosition;
    for (; myPosition < myText.length(); myPosition++) {
      final char ch = myText.charAt(myPosition);
      if (ch == boundaryChar) {
        final String rc = myText.substring(start, myPosition);
        myPosition++;
        return rc;
      }
      if (!ignoreEol && isEol()) {
        return myText.substring(start, myPosition);
      }
    }
    throw new IllegalStateException("Unexpected text end at " + myPosition);
  }

  /**
   * Check if the next character is the specified one
   *
   * @param c the expected character
   * @return true if the character matches expected.
   */
  public boolean startsWith(final char c) {
    return hasMoreData() && myText.charAt(myPosition) == c;
  }

  /**
   * Check if the rest of the string starts with the specified text
   *
   * @param text the text to check
   * @return true if the text contains the string.
   */
  public boolean startsWith(String text) {
    return myText.startsWith(text, myPosition);
  }

  /**
   * Get text from the current position until the end of the line. After return, the current position is the start of the next line.
   *
   * @return the text until end of the line
   */
  public String line() {
    return line(false);
  }

  /**
   * Get text from the current position until the end of the line. After return, the current position is the start of the next line.
   *
   * @param includeNewLine include new line characters into included string
   * @return the text until end of the line
   */
  public String line(boolean includeNewLine) {
    int start = myPosition;
    while (!isEol()) {
      myPosition++;
    }
    int end;
    if (includeNewLine) {
      nextLine();
      end = myPosition;
    }
    else {
      end = myPosition;
      nextLine();
    }
    return myText.substring(start, end);
  }

  /**
   * Skip specified amount of characters
   *
   * @param n characters to skip
   */
  public void skipChars(final int n) {
    if (n < 0) {
      throw new IllegalArgumentException("Amount of chars to skip must be non neagitve: " + n);
    }
    if (myPosition + n >= myText.length()) {
      throw new IllegalArgumentException("Skipping beyond end of the text (" + myPosition + " + " + n + " >= " + myText.length() + ")");
    }
    myPosition += n;
  }

  /**
   * Try string and consume it if it matches
   *
   * @param c a character to try
   * @return true if the string was consumed.
   */
  public boolean tryConsume(final char c) {
    if (startsWith(c)) {
      skipChars(1);
      return true;
    }
    return false;
  }

  /**
   * Try consuming a sequence of characters
   *
   * @param chars a sequence of characters
   * @return true if consumed successfully
   */
  public boolean tryConsume(String chars) {
    if (startsWith(chars)) {
      skipChars(chars.length());
      return true;
    }
    return false;
  }

  /**
   * @return the next character to be consumed
   */
  public char peek() {
    if (!hasMoreData()) {
      throw new IllegalStateException("There is no next character");
    }
    return myText.charAt(myPosition);
  }
}
