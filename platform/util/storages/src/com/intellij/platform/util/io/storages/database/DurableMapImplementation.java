// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// Selects the persistent layout of a durable map
@ApiStatus.Internal
public enum DurableMapImplementation {
  /// Implementation keeps `(key, value)` entries together, in ~append-only-log
  KEY_VALUE_TOGETHER,
  /// Implementation keeps keys in a dedicated catalog, while values are separated
  KEY_VALUE_SEPARATELY;

  /// @return value from system property `propertyName`, or `defaultValue`
  public static DurableMapImplementation getProperty(@NotNull String propertyName,
                                                     @Nullable DurableMapImplementation defaultValue){
    String value = System.getProperty(propertyName);
    if (value != null) {
      for (DurableMapImplementation implementation : values()) {
        if (implementation.name().equalsIgnoreCase(value)) {
          return implementation;
        }
      }
    }
    return defaultValue;
  }
}
