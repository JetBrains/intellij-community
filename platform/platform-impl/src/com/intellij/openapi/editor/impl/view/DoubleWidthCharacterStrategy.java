// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import org.jetbrains.annotations.ApiStatus;

/**
 * A strategy to differentiate between single and double width characters.
 * @see EditorView#setDoubleWidthCharacterStrategy(DoubleWidthCharacterStrategy)
 */
@ApiStatus.Internal
public interface DoubleWidthCharacterStrategy {
  /**
   * Determines whether a given character is a double-width one.
   * @param codePoint the code point of the input character
   * @return {@code true} if the character is a double-width one, {@code false} otherwise
   */
  boolean isDoubleWidth(int codePoint);
}
