// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find;

import com.intellij.openapi.util.TextRange;

/**
 * Represents the result of a Find operation.
 *
 * @see FindManager#findString(CharSequence, int, FindModel)
 */
public abstract class FindResult extends TextRange {
  public FindResult(int startOffset, int endOffset) {
    super(startOffset, endOffset);
  }

  /**
   * Checks if the find operation was successful.
   *
   * @return true if a string was found by the operation, false otherwise.
   */
  public abstract boolean isStringFound();
}