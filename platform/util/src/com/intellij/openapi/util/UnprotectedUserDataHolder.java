// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Non thread safe version of {@link UserDataHolderBase}.
 */
public class UnprotectedUserDataHolder implements UserDataHolder, UserDataHolderUnprotected {

  private Map<Key, Object> myUserData;

  @SuppressWarnings("unchecked")
  @Nullable
  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    return myUserData != null ? (T)myUserData.get(key) : null;
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    if (myUserData == null) myUserData = ContainerUtil.newHashMap();
    myUserData.put(key, value);
  }

  @Nullable
  @Override
  public <T> T getUserDataUnprotected(@NotNull Key<T> key) {
    return getUserData(key);
  }

  @Override
  public <T> void putUserDataUnprotected(@NotNull Key<T> key, @Nullable T value) {
    putUserData(key, value);
  }
}
