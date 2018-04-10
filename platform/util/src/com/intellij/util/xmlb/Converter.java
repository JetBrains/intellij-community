/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.util.xmlb;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class Converter<T> {
  @Nullable
  public abstract T fromString(@NotNull String value);

  @Nullable
  public abstract String toString(@NotNull T value);
}