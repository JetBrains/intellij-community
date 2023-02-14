// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod;

import com.intellij.refactoring.util.AbstractVariableData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ExtractMethodSettings<T> {
  @NotNull
  String getMethodName();

  AbstractVariableData @NotNull [] getAbstractVariableData();

  @Nullable
  T getVisibility();
}