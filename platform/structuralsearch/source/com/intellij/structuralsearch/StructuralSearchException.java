// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class StructuralSearchException extends RuntimeException {
  public StructuralSearchException() {}

  public StructuralSearchException(@NotNull String message) {
    super(message);
  }

  public StructuralSearchException(@NotNull Throwable cause) {
    super(cause);
  }
}
