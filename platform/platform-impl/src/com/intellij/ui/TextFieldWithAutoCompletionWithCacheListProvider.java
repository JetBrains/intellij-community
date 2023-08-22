// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.util.textCompletion.TextCompletionCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public abstract class TextFieldWithAutoCompletionWithCacheListProvider<T> extends TextFieldWithAutoCompletionListProvider<T> {
  protected TextCompletionCache<T> myCache;

  public TextFieldWithAutoCompletionWithCacheListProvider(@NotNull TextCompletionCache<T> cache) {
    super(Collections.emptyList());
    myCache = cache;
  }

  @Override
  public void setItems(@Nullable Collection<T> variants) {
    // do nothing
  }

  @Override
  public @NotNull Collection<T> getItems(String prefix, boolean cached, CompletionParameters parameters) {
    if (!cached) {
      myCache.updateCache(prefix, parameters);
    }
    return myCache.getItems(prefix, parameters);
  }
}
