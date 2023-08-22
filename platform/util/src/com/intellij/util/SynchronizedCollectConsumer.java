// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class SynchronizedCollectConsumer<T> extends CollectConsumer<T> {
  public SynchronizedCollectConsumer(@NotNull Collection<T> result) {
    super(result);
  }
  public SynchronizedCollectConsumer() {
    super();
  }

  @Override
  public synchronized void consume(T t) {
    super.consume(t);
  }

  @Override
  public synchronized @NotNull Collection<T> getResult() {
    return super.getResult();
  }
}
