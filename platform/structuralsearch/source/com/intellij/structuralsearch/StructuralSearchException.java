// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

/**
 * @author Bas Leijdekkers
 */
public class StructuralSearchException extends RuntimeException {
  public StructuralSearchException() {}

  public StructuralSearchException(String message) {
    super(message);
  }

  public StructuralSearchException(Throwable cause) {
    super(cause);
  }
}
