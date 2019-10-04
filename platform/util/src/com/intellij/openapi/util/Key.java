// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntObjectMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides type-safe access to data.
 *
 * @author max
 * @author Konstantin Bulenkov
 * @see KeyWithDefaultValue
 */
public class Key<T> {
  private static final AtomicInteger ourKeysCounter = new AtomicInteger();
  private static final IntObjectMap<Key<?>> allKeys = ContainerUtil.createConcurrentIntObjectWeakValueMap();

  private final int myIndex = ourKeysCounter.getAndIncrement();
  private final String myName; // for debug purposes only

  public Key(@NotNull String name) {
    myName = name;
    allKeys.put(myIndex, this);
  }

  // Final because some clients depend on one-to-one key index/key instance relationship (e.g. UserDataHolderBase).
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
  public static <T> Key<T> create(@NotNull String name) {
    return new Key<>(name);
  }

  @Contract("null -> null")
  public T get(@Nullable UserDataHolder holder) {
    return holder == null ? null : holder.getUserData(this);
  }

  @Contract("null -> null")
  public T get(@Nullable Map<Key, ?> holder) {
    //noinspection unchecked
    return holder == null ? null : (T)holder.get(this);
  }

  @Contract("_, !null -> !null")
  public T get(@Nullable UserDataHolder holder, T defaultValue) {
    T t = get(holder);
    return t == null ? defaultValue : t;
  }

  @NotNull
  public T getRequired(@NotNull UserDataHolder holder) {
    return ObjectUtils.notNull(holder.getUserData(this));
  }

  /**
   * Returns {@code true} if and only if the {@code holder} has not null value for the key.
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
   * @deprecated access to a key via its name is a dirty hack; use Key instance directly instead
   */
  @Deprecated
  @Nullable
  public static Key<?> findKeyByName(String name) {
    for (IntObjectMap.Entry<Key<?>> key : allKeys.entrySet()) {
      if (name.equals(key.getValue().myName)) {
        return key.getValue();
      }
    }
    return null;
  }
}