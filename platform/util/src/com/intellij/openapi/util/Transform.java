/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.util;

import org.jetbrains.annotations.ApiStatus;

/**
 * @deprecated use {@link java.util.function.Function} instead
 */
@Deprecated
@ApiStatus.ScheduledForRemoval
public interface Transform<S, T> {
  /**
   * @deprecated use {@link java.util.function.Function} instead
   */
  @Deprecated
  T transform(S s);
}