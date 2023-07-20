// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Non thread safe version of {@link UserDataHolderBase}.
 */
public class UnprotectedUserDataHolder implements UserDataHolder, UserDataHolderUnprotected {

  private Map<Key, Object> myUserData;

  @Override
  public @Nullable <T> T getUserData(@NotNull Key<T> key) {
    //noinspection unchecked
    T value = myUserData != null ? (T)myUserData.get(key) : null;
    if (value == null && key instanceof KeyWithDefaultValue) {
      value = ((KeyWithDefaultValue<T>)key).getDefaultValue();
      putUserData(key, value);
    }
    return value;
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    if (myUserData == null) myUserData = new HashMap<>();
    myUserData.put(key, value);
  }

  @Override
  public @Nullable <T> T getUserDataUnprotected(@NotNull Key<T> key) {
    return getUserData(key);
  }

  @Override
  public <T> void putUserDataUnprotected(@NotNull Key<T> key, @Nullable T value) {
    putUserData(key, value);
  }
}
