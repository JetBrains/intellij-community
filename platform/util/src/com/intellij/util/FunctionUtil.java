/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class FunctionUtil {
  private FunctionUtil() { }

  @NotNull
  public static <T> Function<T, T> id() {
    //noinspection unchecked
    return (Function<T, T>)Function.ID;
  }

  @NotNull
  public static <A, B> NullableFunction<A, B> nullConstant() {
    //noinspection unchecked
    return (NullableFunction<A, B>)NullableFunction.NULL;
  }

  @NotNull
  public static <T> Function<T, String> string() {
    //noinspection unchecked
    return (Function<T, String>)Function.TO_STRING;
  }

  @NotNull
  public static <A, B> Function<A, B> constant(final B b) {
    return a -> b;
  }

  @NotNull
  public static <A, B, C> NotNullFunction<A, C> composition(@NotNull final NotNullFunction<? super B, ? extends C> f, @NotNull final NotNullFunction<? super A, ? extends B> g) {
    return a -> f.fun(g.fun(a));
  }
}
