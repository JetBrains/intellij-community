// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;


/**
 * Wraps {@linkplain DataEnumeratorEx} instance and prohibit modifications: i.e. {@linkplain #enumerate(Object)}-ing
 * of values not already enumerated will throw {@link IllegalStateException}.
 */
public class UnmodifiableEnumeratorWrapper<Data> implements DataEnumeratorEx<Data> {

  private final @NotNull DataEnumeratorEx<Data> wrappedEnumerator;

  public UnmodifiableEnumeratorWrapper(final @NotNull DataEnumeratorEx<Data> enumeratorToWrap) {
    wrappedEnumerator = enumeratorToWrap;
  }

  /**
   * @throws IllegalStateException if given value was not already enumerated (assigned id) by wrapped
   *                               enumerator
   */
  @Override
  public int enumerate(final @Nullable Data value) throws IOException {
    final int id = wrappedEnumerator.tryEnumerate(value);
    if (id == NULL_ID) {
      throw new IllegalStateException("Unmodifiable enumerator can't .enumerate() new values, but .enumerate(" + value + ") was requested");
    }
    return id;
  }

  @Override
  public int tryEnumerate(final @Nullable Data value) throws IOException {
    return wrappedEnumerator.tryEnumerate(value);
  }

  @Override
  public @Nullable Data valueOf(final int id) throws IOException {
    return wrappedEnumerator.valueOf(id);
  }
}