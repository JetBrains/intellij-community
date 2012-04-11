/*
 * Copyright 2000-2009 JetBrains s.r.o.
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


import com.intellij.util.SmartFMap;
import com.intellij.util.concurrency.AtomicFieldUpdater;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Map;

public class UserDataHolderBase implements UserDataHolderEx, Cloneable {
  private static final Key<SmartFMap<Key, Object>> COPYABLE_USER_MAP_KEY = Key.create("COPYABLE_USER_MAP_KEY");

  /**
   * Concurrent writes to this field are via CASes only, using the {@link #updater}
   */
  @NotNull private volatile SmartFMap<Key, Object> myUserMap = SmartFMap.emptyMap();

  protected Object clone() {
    try {
      UserDataHolderBase clone = (UserDataHolderBase)super.clone();
      clone.myUserMap = SmartFMap.emptyMap();
      copyCopyableDataTo(clone);
      return clone;
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);

    }
  }

  @TestOnly
  public String getUserDataString() {
    final SmartFMap<Key, Object> userMap = myUserMap;
    final Map copyableMap = getUserData(COPYABLE_USER_MAP_KEY);
    return userMap.toString() + (copyableMap == null ? "" : copyableMap.toString());
  }

  public void copyUserDataTo(UserDataHolderBase other) {
    other.myUserMap = myUserMap;
  }

  public <T> T getUserData(@NotNull Key<T> key) {
    //noinspection unchecked
    return (T)myUserMap.get(key);
  }

  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    while (true) {
      SmartFMap<Key, Object> map = myUserMap;
      SmartFMap<Key, Object> newMap = value == null ? map.minus(key) : map.plus(key, value);
      if (newMap == map || updater.compareAndSet(this, map, newMap)) {
        return;
      }
    }
  }

  public <T> T getCopyableUserData(Key<T> key) {
    SmartFMap<Key, Object> map = getUserData(COPYABLE_USER_MAP_KEY);
    //noinspection unchecked,ConstantConditions
    return map == null ? null : (T)map.get(key);
  }

  public <T> void putCopyableUserData(Key<T> key, T value) {
    while (true) {
      SmartFMap<Key, Object> map = myUserMap;
      @SuppressWarnings("unchecked") SmartFMap<Key, Object> copyableMap = (SmartFMap<Key, Object>)map.get(COPYABLE_USER_MAP_KEY);
      if (copyableMap == null) {
        copyableMap = SmartFMap.emptyMap();
      }
      SmartFMap<Key, Object> newCopyableMap = value == null ? copyableMap.minus(key) : copyableMap.plus(key, value);
      SmartFMap<Key, Object> newMap = newCopyableMap.isEmpty() ? map.minus(COPYABLE_USER_MAP_KEY) : map.plus(COPYABLE_USER_MAP_KEY, newCopyableMap);
      if (newMap == map || updater.compareAndSet(this, map, newMap)) {
        return;
      }
    }
  }

  public <T> boolean replace(@NotNull Key<T> key, @Nullable T oldValue, @Nullable T newValue) {
    while (true) {
      SmartFMap<Key, Object> map = myUserMap;
      if (map.get(key) != oldValue) {
        return false;
      }
      SmartFMap<Key, Object> newMap = newValue == null ? map.minus(key) : map.plus(key, newValue);
      if (newMap == map || updater.compareAndSet(this, map, newMap)) {
        return true;
      }
    }
  }
                                                            
  @NotNull
  public <T> T putUserDataIfAbsent(@NotNull final Key<T> key, @NotNull final T value) {
    while (true) {
      SmartFMap<Key, Object> map = myUserMap;
      @SuppressWarnings("unchecked") T oldValue = (T)map.get(key);
      if (oldValue != null) {
        return oldValue;
      }
      SmartFMap<Key, Object> newMap = map.plus(key, value);
      if (newMap == map || updater.compareAndSet(this, map, newMap)) {
        return value;
      }
    }
  }

  public void copyCopyableDataTo(@NotNull UserDataHolderBase clone) {
    clone.putUserData(COPYABLE_USER_MAP_KEY, getUserData(COPYABLE_USER_MAP_KEY));
  }

  protected void clearUserData() {
    myUserMap = SmartFMap.emptyMap();
  }

  private static final AtomicFieldUpdater<UserDataHolderBase, SmartFMap> updater = AtomicFieldUpdater.forFieldOfType(UserDataHolderBase.class, SmartFMap.class);
}
