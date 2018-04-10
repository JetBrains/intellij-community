/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntObjectMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides type-safe access to data.
 *
 * @author max
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
public class Key<T> {
  private static final AtomicInteger ourKeysCounter = new AtomicInteger();
  private final int myIndex = ourKeysCounter.getAndIncrement();
  private final String myName; // for debug purposes only
  private static final IntObjectMap<Key> allKeys = ContainerUtil.createConcurrentIntObjectWeakValueMap();

  public Key(@NotNull @NonNls String name) {
    myName = name;
    allKeys.put(myIndex, this);
  }

  // made final because many classes depend on one-to-one key index <-> key instance relationship. See e.g. UserDataHolderBase
  @Override
  public final int hashCode() {
    return myIndex;
  }

  @Override
  public final boolean equals(Object obj) {
    return obj == this;
  }

  @Override
  public String toString() {
    return myName;
  }

  @NotNull
  public static <T> Key<T> create(@NotNull @NonNls String name) {
    return new Key<T>(name);
  }

  public T get(@Nullable UserDataHolder holder) {
    return holder == null ? null : holder.getUserData(this);
  }

  public T get(@Nullable Map<Key, ?> holder) {
    //noinspection unchecked
    return holder == null ? null : (T)holder.get(this);
  }

  public T get(@Nullable UserDataHolder holder, T defaultValue) {
    final T t = get(holder);
    return t == null ? defaultValue : t;
  }

  @NotNull
  public T getRequired(@NotNull UserDataHolder holder) {
    return ObjectUtils.notNull(holder.getUserData(this));
  }

  /**
   * Returns {@code true} if and only if the {@code holder} has
   * not null value by the key.
   *
   * @param holder user data holder object
   * @return {@code true} if holder.getUserData(this) != null
   * {@code false} otherwise.
   */
  public boolean isIn(@Nullable UserDataHolder holder) {
    return get(holder) != null;
  }

  public void set(@Nullable UserDataHolder holder, @Nullable T value) {
    if (holder != null) {
      holder.putUserData(this, value);
    }
  }

  public void set(@Nullable Map<Key, Object> holder, T value) {
    if (holder != null) {
      holder.put(this, value);
    }
  }

  @Nullable("can become null if the key has been gc-ed")
  public static <T> Key<T> getKeyByIndex(int index) {
    //noinspection unchecked
    return (Key<T>)allKeys.get(index);
  }

  /**
   * @deprecated access to Key via its name is a kind of hack, use Key instance directly instead
   */
  @Nullable
  public static Key<?> findKeyByName(String name) {
    for (IntObjectMap.Entry<Key> key : allKeys.entries()) {
      if (name.equals(key.getValue().myName)) {
        //noinspection unchecked
        return key.getValue();
      }
    }
    return null;
  }
}