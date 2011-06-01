package com.intellij.util.diff;

/**
 * @author irengrig
 *         Date: 5/28/11
 *         Time: 10:30 PM
 */
public class FilesTooBigForDiffException extends Exception {
  private final int myNumLines;

  public FilesTooBigForDiffException(int numLines) {
    super("Can not calculate diff. File is too big and there are too many changes.");
    myNumLines = numLines;
  }

  public int getNumLines() {
    return myNumLines;
  }
}
