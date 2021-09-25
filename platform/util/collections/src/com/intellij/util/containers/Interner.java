// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

public abstract class Interner<T> {
  /**
   * Allow to reuse structurally equal objects to avoid memory being wasted on them. Objects are cached on weak references
   * and garbage-collected when not needed anymore.
   */
  public static @NotNull <T> Interner<T> createWeakInterner() {
    // weak interner exposes TObjectHashingStrategy
    //noinspection deprecation
    return new WeakInterner<>();
  }

  public static @NotNull Interner<String> createStringInterner() {
    // weak interner exposes TObjectHashingStrategy
    return new HashSetInterner<>();
  }

  @NotNull
  public abstract T intern(@NotNull T name);

  public abstract void clear();

  @NotNull
  public abstract Set<T> getValues();
}
