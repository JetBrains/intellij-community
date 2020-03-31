/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.util;

/**
 * @deprecated use {@link java.util.function.Function} instead
 */
@Deprecated
public interface Transform<S, T> {
  /**
   * @deprecated use {@link java.util.function.Function} instead
   */
  @Deprecated
  T transform(S s);
}