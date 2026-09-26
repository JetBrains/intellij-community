// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.textCompletion;

import com.intellij.codeInsight.completion.CompletionParameters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface TextCompletionCache<T> {
  void setItems(@NotNull Collection<T> items);

  Collection<T> getItems(@NotNull String prefix, @Nullable CompletionParameters parameters);

  void updateCache(@NotNull String prefix, @Nullable CompletionParameters parameters);
}
