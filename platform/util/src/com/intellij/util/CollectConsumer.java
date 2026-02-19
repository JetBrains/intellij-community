// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class CollectConsumer<T> implements Consumer<T>, java.util.function.Consumer<T> {
  private final Collection<T> myResult;

  public CollectConsumer(@NotNull Collection<T> result) {
    myResult = result;
  }

  public CollectConsumer() {
    this(new SmartList<>());
  }

  @Override
  public void accept(T t) {
    myResult.add(t);
  }

  public @NotNull Collection<T> getResult() {
    return myResult;
  }

  @Override
  public void consume(T t) {
    accept(t);
  }
}
