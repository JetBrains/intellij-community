// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows storing custom user data within a model object. This might be preferred to an explicit Map with model objects as keys and
 * custom data in values because this allows the data to be garbage-collected together with the values.
 * <p>
 * If you need to implement this interface, extend {@link UserDataHolderBase} or delegate to its instance instead of writing your own implementation.
 */
public interface UserDataHolder {
  /**
   * @return a user data value associated with this object. Doesn't require read action.
   */
  @Nullable <T> T getUserData(@NotNull Key<T> key);

  /**
   * Add a new user data value to this object. Doesn't require write action.
   */
  <T> void putUserData(@NotNull Key<T> key, @Nullable T value);
}