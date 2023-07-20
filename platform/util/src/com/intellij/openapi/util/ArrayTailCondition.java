// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;

@ScheduledForRemoval
@Deprecated
public interface ArrayTailCondition<T> {
  ArrayTailCondition TRUE = new ArrayTailCondition() {
    @Override
    public boolean value(final Object[] array, final int start) {
      return true;
    }
  };

  boolean value(T[] array, int start);
}
