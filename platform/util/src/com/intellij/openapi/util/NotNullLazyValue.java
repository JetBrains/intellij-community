// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Compute-once keep-forever lazy value.
 * Do not extend, but use static factory methods to create instance.
 * <p/>
 * Clearable version: {@link ClearableLazyValue}.
 */
@ApiStatus.NonExtendable
public abstract class NotNullLazyValue<T> implements Supplier<T> {
  private T myValue;

  /** @deprecated Use {@link NotNullLazyValue#lazy(Supplier)} */
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  @Deprecated
  @SuppressWarnings("DeprecatedIsStillUsed")
  protected NotNullLazyValue() { }

  protected abstract @NotNull T compute();

  @Override
  public final T get() {
    return getValue();
  }

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

  @SuppressWarnings("deprecation")
  public static @NotNull <T> NotNullLazyValue<T> atomicLazy(@NotNull Supplier<? extends @NotNull T> value) {
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
  public static @NotNull <T> NotNullLazyValue<T> volatileLazy(@NotNull Supplier<? extends @NotNull T> supplier) {
    return new NotNullLazyValue<T>() {
      private volatile T value;

      @Override
      public @NotNull T getValue() {
        T value = this.value;
        if (value == null) {
          RecursionGuard.StackStamp stamp = RecursionManager.markStack();
          value = compute();
          if (stamp.mayCacheNow()) {
            this.value = value;
          }
        }
        return value;
      }

      @Override
      public boolean isComputed() {
        return value != null;
      }

      @Override
      protected @NotNull T compute() {
        return supplier.get();
      }
    };
  }
}
