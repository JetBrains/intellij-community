// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.util;

import com.intellij.openapi.vcs.VcsException;
import git4idea.i18n.GitBundle;
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
  public StringScanner(final @NotNull String text) {
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
    return ch == '\n';
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
        if (ch == '\r' && ch2 == '\n') {
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
  public String spaceToken() throws VcsException {
    return boundedToken(' ');
  }

  /**
   * Gets next token that is ended by tab or new line. Consumes tab but not a new line.
   * Start position is the current. So if the string starts with space a empty token is returned.
   *
   * @return a token
   */
  public String tabToken() throws VcsException {
    return boundedToken('\t');
  }

  /**
   * Gets next token that is ended by {@code boundaryChar} or new line. Consumes {@code boundaryChar} but not a new line.
   * Start position is the current. So if the string starts with {@code boundaryChar} a empty token is returned.
   *
   * @param boundaryChar a boundary character
   * @return a token
   */
  public @NotNull String boundedToken(final char boundaryChar) throws VcsException {
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
  public @NotNull String boundedToken(char boundaryChar, boolean ignoreEol) throws VcsException {
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
    throw unexpectedTextEndException(start);
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
  public void skipChars(final int n) throws VcsException {
    if (n < 0) {
      throw new IllegalArgumentException("Amount of chars to skip must be non neagitve: " + n);
    }
    if (myPosition + n >= myText.length()) {
      throw unexpectedTextEndException(myPosition);
    }
    myPosition += n;
  }

  /**
   * @return the next character to be consumed
   */
  public char peek() throws VcsException {
    if (!hasMoreData()) {
      throw unexpectedTextEndException(myPosition);
    }
    return myText.charAt(myPosition);
  }

  public String getAllText() {
    return myText;
  }

  private @NotNull VcsException unexpectedTextEndException(int start) {
    start = boundToText(start);

    int firstWindowStart = boundToText(start - 10);
    int firstWindowEnd = boundToText(start + 10);
    int secondWindowStart = boundToText(myText.length() - 10);
    int secondWindowEnd = boundToText(myText.length());
    String context;
    if (secondWindowEnd - firstWindowStart < 50) {
      context = myText.substring(firstWindowStart, start) + "<!>" + myText.substring(start, secondWindowEnd);
    }
    else {
      context = myText.substring(firstWindowStart, start) + "<!>" + myText.substring(start, firstWindowEnd) + "..." +
                myText.substring(secondWindowStart, secondWindowEnd);
    }
    return new VcsException(GitBundle.message("output.parse.exception.unexpected.end.message", context));
  }

  private int boundToText(int offset) {
    return Math.max(0, Math.min(myText.length(), offset));
  }
}
