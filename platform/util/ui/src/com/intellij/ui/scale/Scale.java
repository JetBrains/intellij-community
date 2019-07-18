// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.scale;

import gnu.trove.TDoubleObjectHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;

/**
 * A scale factor value of {@link ScaleType}.
 */
public final class Scale {
  final double value;
  final ScaleType type;

  // The cache radically reduces potentially thousands of equal Scale instances.
  private static final ThreadLocal<EnumMap<ScaleType, TDoubleObjectHashMap<Scale>>> cache =
    ThreadLocal.withInitial(() -> new EnumMap<>(ScaleType.class));

  @NotNull
  public static Scale create(double value, @NotNull ScaleType type) {
    EnumMap<ScaleType, TDoubleObjectHashMap<Scale>> enumMap = cache.get();
    TDoubleObjectHashMap<Scale> map = enumMap.get(type);
    if (map == null) {
      enumMap.put(type, map = new TDoubleObjectHashMap<>());
    }
    Scale scale = map.get(value);
    if (scale != null) return scale;
    map.put(value, scale = new Scale(value, type));
    return scale;
  }

  private Scale(double value, @NotNull ScaleType type) {
    this.value = value;
    this.type = type;
  }

  public double value() {
    return value;
  }

  @NotNull
  public ScaleType type() {
    return type;
  }

  @Override
  public boolean equals(Object obj) {
    return this == obj; // can rely on default impl due to caching
  }

  @Override
  public String toString() {
    return "[" + type.name() + " " + value + "]";
  }
}
