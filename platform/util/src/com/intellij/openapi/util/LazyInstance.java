// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;

/**
 * @deprecated Use {@link com.intellij.openapi.components.ComponentManager#instantiateClass}
 */
@ApiStatus.ScheduledForRemoval
@Deprecated
public abstract class LazyInstance<T> extends NotNullLazyValue<T> {
  protected abstract Class<T> getInstanceClass() throws ClassNotFoundException;

  @Override
  protected final @NotNull T compute() {
    try {
      Class<T> tClass = getInstanceClass();
      Constructor<T> constructor = tClass.getDeclaredConstructor();
      constructor.setAccessible(true);
      return tClass.newInstance();
    }
    catch (InstantiationException | NoSuchMethodException | ClassNotFoundException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
