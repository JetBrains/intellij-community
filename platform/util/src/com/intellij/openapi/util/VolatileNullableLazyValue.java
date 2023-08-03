// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.NonExtendable
public abstract class VolatileNullableLazyValue<T> extends NullableLazyValue<T> {
  private volatile boolean myComputed;
  private volatile @Nullable T myValue;

  /** @deprecated please use {@link NullableLazyValue#volatileLazyNullable} instead */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  VolatileNullableLazyValue() { }

  @Override
  @SuppressWarnings("DuplicatedCode")
  public @Nullable T getValue() {
    boolean computed = myComputed;
    T value = myValue;
    if (!computed) {
      RecursionGuard.StackStamp stamp = RecursionManager.markStack();
      value = compute();
      if (stamp.mayCacheNow()) {
        myValue = value;
        myComputed = true;
      }
    }
    return value;
  }

  @Override
  public boolean isComputed() {
    return myComputed;
  }
}
