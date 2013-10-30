/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.util.containers;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class ImmutableUserMap {
  public static final ImmutableUserMap EMPTY = new ImmutableUserMap() {
    @Override
    public <T> T get(@NotNull final Key<T> key) {
      return null;
    }
  };

  private ImmutableUserMap() {
  }

  public abstract <T> T get(@NotNull Key<T> key);

  public final <T> ImmutableUserMap put(@NotNull final Key<T> key, final T value) {
    return new ImmutableUserMapImpl<T>(key, value, this);
  }

  private static class ImmutableUserMapImpl<V> extends ImmutableUserMap {
    private final Key<V> myKey;
    private final V myValue;
    private final ImmutableUserMap myNext;

    private ImmutableUserMapImpl(final Key<V> key, final V value, final ImmutableUserMap next) {
      myKey = key;
      myNext = next;
      myValue = value;
    }

    @Override
    public <T> T get(@NotNull final Key<T> key) {
      if (key.equals(myKey)) return (T)myValue;
      return myNext.get(key);
    }

  }
}
