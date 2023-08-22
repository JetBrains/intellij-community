// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface UserDataHolderEx extends UserDataHolder {
  /**
   * @return written or already existing value
   */
  @NotNull <T> T putUserDataIfAbsent(@NotNull Key<T> key, @NotNull T value);

  /**
   * Replaces (atomically) old value in the map with the new one
   *
   * @return true if old value got replaced, false otherwise
   * @see java.util.concurrent.ConcurrentMap#replace(Object, Object, Object)
   */
  <T> boolean replace(@NotNull Key<T> key, @Nullable T oldValue, @Nullable T newValue);
}
