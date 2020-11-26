// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Compute-once keep-forever lazy value.
 * Clearable version: {@link ClearableLazyValue}.
 */
@ApiStatus.NonExtendable
public abstract class NotNullLazyValue<T> {
  private T myValue;

  /**
   * @deprecated Use {@link NotNullLazyValue#lazy(Supplier)}
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  protected NotNullLazyValue() {
  }

  protected abstract @NotNull T compute();

  public @NotNull T getValue() {
    T result = myValue;
    if (result == null) {
      RecursionGuard.StackStamp stamp = RecursionManager.markStack();
      result = compute();
      if (stamp.mayCacheNow()) {
        myValue = result;
      }
    }
    return result;
  }

  public boolean isComputed() {
    return myValue != null;
  }

  public static @NotNull <T> NotNullLazyValue<T> createConstantValue(@NotNull T value) {
    return lazy(() -> value);
  }

  public static @NotNull <T> NotNullLazyValue<T> createValue(@NotNull NotNullFactory<? extends T> value) {
    return lazy(value::create);
  }

  public static @NotNull <T> NotNullLazyValue<T> lazy(@NotNull Supplier<? extends T> value) {
    return new NotNullLazyValue<T>() {
      @Override
      protected @NotNull T compute() {
        return value.get();
      }
    };
  }

  public static @NotNull <T> NotNullLazyValue<T> atomicLazy(@NotNull Supplier<@NotNull ? extends T> value) {
    //noinspection deprecation
    return new AtomicNotNullLazyValue<T>() {
      @Override
      protected @NotNull T compute() {
        return value.get();
      }
    };
  }

  /**
   * Assumes that values computed by different threads are equal and interchangeable
   * and readers should be ready to get different instances on different invocations of the {@link #getValue()}.
   */
  @NotNull
  public static <T> NotNullLazyValue<T> volatileLazy(@NotNull Supplier<@NotNull ? extends T> value) {
    return new VolatileNotNullLazyValue<T>() {
      @NotNull
      @Override
      protected T compute() {
        return value.get();
      }
    };
  }
}