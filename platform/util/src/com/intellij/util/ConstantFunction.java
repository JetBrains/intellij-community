// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class ConstantFunction<Param, Result> implements NotNullFunction<Param, Result> {
  private final Result value;

  public ConstantFunction(@NotNull Result value) {
    this.value = value;
  }

  @Override
  public @NotNull Result fun(Param param) {
    return value;
  }
}
