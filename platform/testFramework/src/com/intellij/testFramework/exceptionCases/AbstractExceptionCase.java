// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.exceptionCases;

import com.intellij.util.ThrowableRunnable;

/**
 * Base class of block, annotated with exception. Inheritors of this
 * class specifies concrete Exception classes
 * @deprecated Use {@link com.intellij.testFramework.UsefulTestCase#assertThrows(Class, ThrowableRunnable)}
 * or {@link com.intellij.testFramework.UsefulTestCase#assertNoException(Class, ThrowableRunnable)} instead
 */
@Deprecated(forRemoval = true)
public abstract class AbstractExceptionCase<T extends Throwable> {
  public abstract Class<T> getExpectedExceptionClass();

  /**
   * Suspicious code must be in implementation of this closure
   */
  public abstract void tryClosure() throws T;
}
