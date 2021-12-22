// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.diff;

/**
 * @author irengrig
 */
public class FilesTooBigForDiffException extends Exception {
  public FilesTooBigForDiffException() {
    super("Can not calculate diff. File is too big and there are too many changes.");
  }
}
