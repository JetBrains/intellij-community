// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb;

import com.intellij.util.serialization.MutableAccessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;

public interface Serializer {
  @NotNull
  Binding getClassBinding(@NotNull Class<?> aClass, @NotNull Type originalType, @Nullable MutableAccessor accessor);

  Binding getClassBinding(@NotNull Class<?> aClass);

  @Nullable
  Binding getBinding(@NotNull MutableAccessor accessor);

  @Nullable
  Binding getBinding(@NotNull Type type);

  @Nullable
  Binding getBinding(@NotNull Class<?> aClass, @NotNull Type type);
}
