// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Like UserDataHolder, but stores data in non-thread-safe way.
 * Should not be accessed across threads.
 *
 * @deprecated use {@link UserDataHolder}. <br/>
 * This interface is extended only by {@link com.intellij.lang.PsiBuilder} which is used in a single thread always.
 * {@link UserDataHolder} doesn't make any restrictions on thread-safety, so it's now used in PsiBuilder instead,
 * and this interface is left for binary compatibility.
 */
@Deprecated
@ApiStatus.ScheduledForRemoval
public interface UserDataHolderUnprotected {

  /**
   * @deprecated use {@link UserDataHolder}
   */
  @Deprecated
  @Nullable
  <T> T getUserDataUnprotected(@NotNull Key<T> key);

  /**
   * @deprecated use {@link UserDataHolder}
   */
  @Deprecated
  <T> void putUserDataUnprotected(@NotNull Key<T> key, @Nullable T value);
}
