// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

/**
 * TextRange with arbitrary offsets, not intended to be checked by {@link TextRange#assertProperRange(int, int, Object)}.
 * Please use with caution.
 *
 * @author Dmitry Avdeev
 */
public final class UnfairTextRange extends TextRange {

  public UnfairTextRange(int startOffset, int endOffset) {
    super(startOffset, endOffset, false);
  }
}
