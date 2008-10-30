/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

/**
 * A parser of strings that is oriented to scanning typlical git outputs
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
  public StringScanner(final String text) {
    myText = text;
    myPosition = 0;
  }

  /**
   * @return true if ther is more data available
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
    int start = myPosition;
    for (; myPosition < myText.length(); myPosition++) {
      final char ch = myText.charAt(myPosition);
      if (ch == ' ') {
        final String rc = myText.substring(start, myPosition);
        myPosition++;
        return rc;
      }
      if (isEol()) {
        return myText.substring(start, myPosition);
      }
    }
    throw new IllegalStateException("Unexpected text end at " + myPosition);
  }

  /**
   * Check if the next character is the specified one
   *
   * @param c the expecte character
   * @return true if the character matches expected.
   */
  public boolean startsWith(final char c) {
    return hasMoreData() && myText.charAt(myPosition) == c;
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
}
