// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntObjectMap;
import org.jetbrains.annotations.*;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides type-safe access to data.
 * <p>
 * Implementation note: Please don't create too many instances of this class, because internal maps could overflow.
 * Instead, store the Key instance in a private static field and use it from outside.
 * For example,
 * <pre>
 * {@code
 *   class KeyUsage {
 *     private static final Key<String> MY_NAME_KEY = Key.create("my name");
 *     String getName() { return getData(MY_NAME_KEY); }
 *   }
 * }
 * </pre>
 *
 * @author max
 * @author Konstantin Bulenkov
 * @see KeyWithDefaultValue
 */
public @NonNls class Key<T> {
  private static final AtomicInteger ourKeysCounter = new AtomicInteger();
  private static final IntObjectMap<Key<?>> allKeys = ContainerUtil.createIntKeyWeakValueMap();
  private final int myIndex = ourKeysCounter.getAndIncrement();
  private final String myName; // for debug purposes only

  public Key(@NonNls @NotNull String name) {
    myName = name;
    synchronized (allKeys) {
      allKeys.put(myIndex, this);
    }
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

  public static @NotNull <T> Key<T> create(@NonNls @NotNull String name) {
    return new Key<>(name);
  }

  @Contract("null -> null")
  public @UnknownNullability T get(@Nullable UserDataHolder holder) {
    return holder == null ? null : holder.getUserData(this);
  }

  @Contract("_, !null -> !null")
  public T get(@Nullable UserDataHolder holder, T defaultValue) {
    T t = get(holder);
    return t == null ? defaultValue : t;
  }

  public @NotNull T getRequired(@NotNull UserDataHolder holder) {
    return Objects.requireNonNull(holder.getUserData(this));
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

  public static @Nullable("can become null if the key has been gc-ed") <T> Key<T> getKeyByIndex(int index) {
    synchronized (allKeys) {
      //noinspection unchecked
      return (Key<T>)allKeys.get(index);
    }
  }

  /**
   * @deprecated access to a key via its name is a dirty hack; use Key instance directly instead
   */
  @Deprecated
  public static @Nullable Key<?> findKeyByName(@NotNull String name) {
    synchronized (allKeys) {
      for (Key<?> key : allKeys.values()) {
        if (name.equals(key.myName)) {
          return key;
        }
      }
      return null;
    }
  }
}