// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
@ApiStatus.Experimental
public final class ConcatenationQuery<T> extends AbstractQuery<T> {

  private final List<Query<T>> myQueryList;

  public ConcatenationQuery(@NotNull List<Query<T>> queryList) {
    this.myQueryList = queryList;
  }

  @Override
  protected boolean processResults(@NotNull Processor<? super T> consumer) {
    for (var query : myQueryList) {
      if (!delegateProcessResults(query, consumer)) return false;
    }
    return true;
  }
}

