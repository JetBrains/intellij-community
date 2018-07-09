/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;

/** @deprecated use {@link Pair} (to be removed in IDEA 2018) */
@Deprecated
public class KeyValue<Key, Value> extends Pair<Key, Value> {
  public KeyValue(Key key, Value value) {
    super(key, value);
  }

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "deprecation"})
  @NotNull
  public static <Key, Value> KeyValue<Key, Value> create(final Key key, final Value value) {
    return new KeyValue<Key, Value>(key, value);
  }

  public Key getKey() {
    return first;
  }

  public Value getValue() {
    return second;
  }
}