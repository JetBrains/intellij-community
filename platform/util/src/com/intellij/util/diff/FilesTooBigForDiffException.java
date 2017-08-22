package com.intellij.util.diff;

import com.intellij.openapi.util.registry.Registry;

/**
 * @author irengrig
 *         Date: 5/28/11
 *         Time: 10:30 PM
 */
public class FilesTooBigForDiffException extends Exception {
  public static final int DELTA_THRESHOLD_SIZE = Registry.intValue("diff.delta.threshold.size");
  // Do not try to compare two lines by-word after this much fails.
  public static final int MAX_BAD_LINES = 3;

  public FilesTooBigForDiffException() {
    super("Can not calculate diff. File is too big and there are too many changes.");
  }
}
