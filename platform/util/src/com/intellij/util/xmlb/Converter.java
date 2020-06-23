// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class Converter<T> {
  public abstract @Nullable T fromString(@NotNull String value);

  public abstract @Nullable String toString(@NotNull T value);
}