// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FunctionUtil {
  private static final NullableFunction<?, ?> NULL = new NullableFunction<Object, Object>() {
    @Override
    public Object fun(final Object o) {
      return null;
    }

    @Override
    public String toString() {
      return "NullableFunction.NULL";
    }
  };

  private FunctionUtil() { }

  @NotNull
  public static <T> Function<T, T> id() {
    return Functions.identity();
  }

  @NotNull
  public static <A, B> NullableFunction<A, B> nullConstant() {
    //noinspection unchecked
    return (NullableFunction<A, B>)NULL;
  }

  @NotNull
  public static <T> Function<T, String> string() {
    return Functions.TO_STRING();
  }

  @NotNull
  public static <A, B> Function<A, B> constant(final B b) {
    return a -> b;
  }

  @NotNull
  public static <A, B, C> NotNullFunction<A, C> composition(@NotNull final NotNullFunction<? super B, ? extends C> f, @NotNull final NotNullFunction<? super A, ? extends B> g) {
    return a -> f.fun(g.fun(a));
  }

  /**
   * Returns a runnable which runs both supplied runnables. If any of them throws, the second one is still executed.
   * If both throw, the second exception is added to the first one as suppressed.
   *
   * @param r1 first runnable to run
   * @param r2 second runnable to run
   * @return composed runnable. If one of arguments is null, returns other argument.
   */
  @Contract(value = "_, null -> param1; null, !null -> param2", pure = true)
  public static Runnable composeRunnables(@Nullable Runnable r1, @Nullable Runnable r2) {
    if (r2 == null) return r1;
    if (r1 == null) return r2;
    return () -> {
      try {
        r1.run();
      }
      catch (RuntimeException | Error ex) {
        try {
          r2.run();
        }
        catch (RuntimeException | Error ex2) {
          ex.addSuppressed(ex2);
        }
        throw ex;
      }
      r2.run();
    };
  }
}
