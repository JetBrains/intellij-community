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


import com.intellij.util.containers.StripedLockConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;

public class UserDataHolderBase implements UserDataHolderEx, Cloneable {
  private static final Object MAP_LOCK = new Object();
  private static final Object COPYABLE_MAP_LOCK = new Object();
  private static final Key<Map<Key, Object>> COPYABLE_USER_MAP_KEY = Key.create("COPYABLE_USER_MAP_KEY");

  private volatile ConcurrentMap<Key, Object> myUserMap = null;

  protected Object clone() {
    try {
      UserDataHolderBase clone = (UserDataHolderBase)super.clone();
      clone.myUserMap = null;
      copyCopyableDataTo(clone);
      return clone;
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);

    }
  }

  @TestOnly
  public String getUserDataString() {
    final ConcurrentMap<Key, Object> userMap = myUserMap;
    if (userMap == null) {
      return "";
    }
    final Map copyableMap = getUserData(COPYABLE_USER_MAP_KEY);
    if (copyableMap == null) {
      return userMap.toString();
    }
    else {
      return userMap.toString() + copyableMap.toString();
    }
  }

  public void copyUserDataTo(UserDataHolderBase other) {
    ConcurrentMap<Key, Object> map = myUserMap;
    if (map == null) {
      other.myUserMap = null;
    }
    else {
      ConcurrentMap<Key, Object> fresh = createDataMap(2);
      fresh.putAll(map);
      other.myUserMap = fresh;
    }
  }

  public <T> T getUserData(@NotNull Key<T> key) {
    final Map<Key, Object> map = myUserMap;
    return map == null ? null : (T)map.get(key);
  }

  public <T> void putUserData(@NotNull Key<T> key, T value) {
    Map<Key, Object> map = getOrCreateMap();

    if (value == null) {
      map.remove(key);
    }
    else {
      map.put(key, value);
    }
  }

  protected ConcurrentMap<Key, Object> createDataMap(int initialCapacity) {
    return new StripedLockConcurrentHashMap<Key, Object>(initialCapacity);
  }

  public <T> T getCopyableUserData(Key<T> key) {
    return getCopyableUserDataImpl(key);
  }

  protected final <T> T getCopyableUserDataImpl(Key<T> key) {
    Map map = getUserData(COPYABLE_USER_MAP_KEY);
    return map == null ? null : (T)map.get(key);
  }

  public <T> void putCopyableUserData(Key<T> key, T value) {
    putCopyableUserDataImpl(key, value);
  }

  protected final <T> void putCopyableUserDataImpl(Key<T> key, T value) {
    synchronized (COPYABLE_MAP_LOCK) {
      Map<Key, Object> copyMap = getUserData(COPYABLE_USER_MAP_KEY);
      if (copyMap == null) {
        if (value == null) return;
        copyMap = createDataMap(1);
        putUserData(COPYABLE_USER_MAP_KEY, copyMap);
      }

      if (value != null) {
        copyMap.put(key, value);
      }
      else {
        copyMap.remove(key);
        if (copyMap.isEmpty()) {
          putUserData(COPYABLE_USER_MAP_KEY, null);
        }
      }
    }
  }

  private ConcurrentMap<Key, Object> getOrCreateMap() {
    ConcurrentMap<Key, Object> map = myUserMap;
    if (map == null) {
      synchronized (MAP_LOCK) {
        map = myUserMap;
        if (map == null) {
          myUserMap = map = createDataMap(2);
        }
      }
    }
    return map;
  }

  public <T> boolean replace(@NotNull Key<T> key, @Nullable T oldValue, @Nullable T newValue) {
    ConcurrentMap<Key, Object> map = getOrCreateMap();
    if (oldValue == null) {
      return newValue == null || map.putIfAbsent(key, newValue) == null;
    }
    if (newValue == null) {
      return map.remove(key, oldValue);
    }
    return map.replace(key, oldValue, newValue);
  }

  @NotNull
  public <T> T putUserDataIfAbsent(@NotNull final Key<T> key, @NotNull final T value) {
    T prev = (T)getOrCreateMap().putIfAbsent(key, value);
    return prev == null ? value : prev;
  }

  public void copyCopyableDataTo(UserDataHolderBase clone) {
    Map<Key, Object> copyableMap = getUserData(COPYABLE_USER_MAP_KEY);
    if (copyableMap != null) {
      ConcurrentMap<Key, Object> copy = createDataMap(copyableMap.size());
      copy.putAll(copyableMap);
      copyableMap = copy;
    }
    clone.putUserData(COPYABLE_USER_MAP_KEY, copyableMap);
  }

  protected void clearUserData() {
    synchronized (MAP_LOCK) {
      myUserMap = null;
    }
  }
}
