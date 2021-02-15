// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.reference.SoftReference;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides type-safe access to data.
 *
 * @author max
 * @author Konstantin Bulenkov
 * @see KeyWithDefaultValue
 */
@NonNls
public class Key<T> {
  private static final AtomicInteger ourKeysCounter = new AtomicInteger();
  private static final Int2ObjectMap<Reference<Key<?>>> allKeys = new Int2ObjectOpenHashMap<>();
  private final int myIndex = ourKeysCounter.getAndIncrement();
  private final String myName; // for debug purposes only

  public Key(@NonNls @NotNull String name) {
    myName = name;
    synchronized (allKeys) {
      allKeys.put(myIndex, new WeakReference<>(this));
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

  @NotNull
  public static <T> Key<T> create(@NonNls @NotNull String name) {
    return new Key<>(name);
  }

  @Contract("null -> null")
  public T get(@Nullable UserDataHolder holder) {
    return holder == null ? null : holder.getUserData(this);
  }

  @Contract("_, !null -> !null")
  public T get(@Nullable UserDataHolder holder, T defaultValue) {
    T t = get(holder);
    return t == null ? defaultValue : t;
  }

  @NotNull
  public T getRequired(@NotNull UserDataHolder holder) {
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

  @Nullable("can become null if the key has been gc-ed")
  public static <T> Key<T> getKeyByIndex(int index) {
    synchronized (allKeys) {
      //noinspection unchecked
      return (Key<T>)SoftReference.dereference(allKeys.get(index));
    }
  }

  /**
   * @deprecated access to a key via its name is a dirty hack; use Key instance directly instead
   */
  @Deprecated
  @Nullable
  public static Key<?> findKeyByName(@NotNull String name) {
    synchronized (allKeys) {
      for (Reference<Key<?>> reference : allKeys.values()) {
        Key<?> key = SoftReference.dereference(reference);
        if (key != null && name.equals(key.myName)) {
          return key;
        }
      }
      return null;
    }
  }
}