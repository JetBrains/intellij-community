// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb;

import com.intellij.serialization.MutableAccessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;

public interface Serializer {
  @NotNull
  Binding getRootBinding(@NotNull Class<?> aClass, @NotNull Type originalType, @NotNull MutableAccessor accessor);

  @NotNull
  Binding getRootBinding(@NotNull Class<?> aClass, @NotNull Type originalType);

  default Binding getRootBinding(@NotNull Class<?> aClass) {
    return getRootBinding(aClass, aClass);
  }

  @Nullable
  Binding getBinding(@NotNull MutableAccessor accessor);

  @Nullable
  Binding getBinding(@NotNull Class<?> aClass, @NotNull Type type);
}
