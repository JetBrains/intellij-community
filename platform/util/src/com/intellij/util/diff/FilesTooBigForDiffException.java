package com.intellij.util.diff;

/**
 * @author irengrig
 *         Date: 5/28/11
 *         Time: 10:30 PM
 */
public class FilesTooBigForDiffException extends Exception {
  public static int MAX_BUFFER_LEN = 10000000; // Limit for memory consumption in IntLCS algorithm. ~ 2000 changed lines, 50Mb memory
  public static int MAX_BAD_LINES = 3; // Do not try to compare two lines by-word after this much fails.

  private final int myNumLines;

  public FilesTooBigForDiffException(int numLines) {
    super("Can not calculate diff. File is too big and there are too many changes.");
    myNumLines = numLines;
  }

  public int getNumLines() {
    return myNumLines;
  }
}
