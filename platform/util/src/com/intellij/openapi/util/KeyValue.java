// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** @deprecated use {@link Pair} */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
public class KeyValue<Key, Value> extends Pair<Key, Value> {
  public KeyValue(Key key, Value value) {
    super(key, value);
  }

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass"})
  @NotNull
  public static <Key, Value> KeyValue<Key, Value> create(final Key key, final Value value) {
    return new KeyValue<>(key, value);
  }

  public Key getKey() {
    return first;
  }

  public Value getValue() {
    return second;
  }
}