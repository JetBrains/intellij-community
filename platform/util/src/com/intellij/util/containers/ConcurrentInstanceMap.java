// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author peter
 */
public final class ConcurrentInstanceMap {
  private ConcurrentInstanceMap() {
  }

  @NotNull
  public static <T> Map<Class<? extends T>,T> create() {
    return ConcurrentFactoryMap.createMap(ConcurrentInstanceMap::calculate);
  }

  @NotNull
  public static <T> T calculate(@NotNull Class<? extends T> key) {
    try {
      return key.newInstance();
    }
    catch (InstantiationException | IllegalAccessException e) {
      throw new RuntimeException("Couldn't instantiate " + key, e);
    }
  }
}