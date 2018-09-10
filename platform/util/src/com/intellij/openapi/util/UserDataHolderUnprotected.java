// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Like UserDataHolder, but stores data in non-thread-safe way.
 * Should not be accessed across threads.
 */
@Deprecated
public interface UserDataHolderUnprotected {

  @Deprecated
  @Nullable
  <T> T getUserDataUnprotected(@NotNull Key<T> key);

  @Deprecated
  <T> void putUserDataUnprotected(@NotNull Key<T> key, @Nullable T value);
}
