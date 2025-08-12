// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.openapi.util.userData.ExternalUserDataStorage;
import com.intellij.util.containers.VarHandleWrapper;
import com.intellij.util.keyFMap.KeyFMap;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.*;

import java.io.Serializable;
import java.util.Objects;
import java.util.function.Supplier;

@ReviseWhenPortedToJDK("11") // rewrite to real VarHandles
@Transient
public class UserDataHolderBase implements UserDataHolderEx, Serializable {
  private static final Key<KeyFMap> COPYABLE_USER_MAP_KEY = Key.create("COPYABLE_USER_MAP_KEY");

  @Nullable
  private static Supplier<ExternalUserDataStorage> ourExternalUserDataStorage = null;

  @ApiStatus.Internal
  public static void setExternalUserDataStorage(@Nullable Supplier<ExternalUserDataStorage> supplier) {
    ourExternalUserDataStorage = supplier;
  }

  private static @Nullable ExternalUserDataStorage externalStorage() {
    Supplier<ExternalUserDataStorage> supplier = ourExternalUserDataStorage;
    return supplier == null ? null : supplier.get();
  }
  private volatile @NotNull KeyFMap value = KeyFMap.EMPTY_MAP;
  private static final VarHandleWrapper VALUE_HANDLE = VarHandleWrapper.getFactory().create(UserDataHolderBase.class, "value", KeyFMap.class);

  public UserDataHolderBase() {
  }

  @Override
  protected Object clone() {
    try {
      UserDataHolderBase clone = (UserDataHolderBase)super.clone();
      clone.setUserMap(KeyFMap.EMPTY_MAP);
      copyCopyableDataTo(clone);
      return clone;
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  @TestOnly
  public String getUserDataString() {
    final KeyFMap userMap = getUserMap();
    final KeyFMap copyableMap = getUserData(COPYABLE_USER_MAP_KEY);
    return userMap + (copyableMap == null ? "" : copyableMap.toString());
  }

  public void copyUserDataTo(@NotNull UserDataHolderBase other) {
    other.setUserMap(getUserMap());
  }

  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    ExternalUserDataStorage external = externalStorage();
    if (external != null) {
      return external.getUserData(this, key);
    }
    else {
      T t = getUserMap().get(key);
      if (t == null && key instanceof KeyWithDefaultValue) {
        t = putUserDataIfAbsent(key, ((KeyWithDefaultValue<T>)key).getDefaultValue());
      }
      return t;
    }
  }

  @ApiStatus.Internal
  public @NotNull KeyFMap getUserMap() {
    ExternalUserDataStorage external = externalStorage();
    if (external != null) {
      return external.getUserMap(this);
    }
    else {
      return value;
    }
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    ExternalUserDataStorage external = externalStorage();
    if (external != null) {
      external.putUserData(this, key, value);
    }
    else {
      while (true) {
        KeyFMap map = getUserMap();
        KeyFMap newMap = value == null ? map.minus(key) : map.plus(key, value);
        if (newMap == map || changeUserMap(map, newMap)) {
          break;
        }
      }
    }
  }

  protected boolean changeUserMap(@NotNull KeyFMap oldMap, @NotNull KeyFMap newMap) {
    return VALUE_HANDLE.compareAndSet(this, oldMap, newMap);
  }

  public <T> @UnknownNullability T getCopyableUserData(@NotNull Key<T> key) {
    KeyFMap map = getUserData(COPYABLE_USER_MAP_KEY);
    return map == null ? null : map.get(key);
  }

  public <T> void putCopyableUserData(@NotNull Key<T> key, T value) {
    ExternalUserDataStorage external = externalStorage();
    if (external != null) {
      while (true) {
        KeyFMap oldCopyableMap = getUserData(COPYABLE_USER_MAP_KEY);
        KeyFMap newCopyableMap = oldCopyableMap;
        if (oldCopyableMap == null) {
          if (value == null) {
            //nothing
          }
          else {
            newCopyableMap = KeyFMap.EMPTY_MAP.plus(key, value);
          }
        }
        else {
          if (value == null) {
            newCopyableMap = oldCopyableMap.minus(key);
          }
          else {
            newCopyableMap = oldCopyableMap.plus(key, value);
          }
        }
        if (
          oldCopyableMap == newCopyableMap ||
          external.compareAndPutUserData(this, COPYABLE_USER_MAP_KEY, oldCopyableMap, newCopyableMap)
        ) {
          break;
        }
      }
    }
    else {
      while (true) {
        KeyFMap map = getUserMap();
        KeyFMap copyableMap = map.get(COPYABLE_USER_MAP_KEY);
        if (copyableMap == null) {
          copyableMap = KeyFMap.EMPTY_MAP;
        }
        KeyFMap newCopyableMap = value == null ? copyableMap.minus(key) : copyableMap.plus(key, value);
        KeyFMap newMap = newCopyableMap.isEmpty() ? map.minus(COPYABLE_USER_MAP_KEY) : map.plus(COPYABLE_USER_MAP_KEY, newCopyableMap);
        if (newMap == map || changeUserMap(map, newMap)) {
          return;
        }
      }
    }
  }

  @Override
  public <T> boolean replace(@NotNull Key<T> key, @Nullable T oldValue, @Nullable T newValue) {
    ExternalUserDataStorage external = externalStorage();
    if (external != null) {
      return external.compareAndPutUserData(this, key, oldValue, newValue);
    }
    else {
      while (true) {
        KeyFMap map = getUserMap();
        if (map.get(key) != oldValue) {
          return false;
        }
        KeyFMap newMap = newValue == null ? map.minus(key) : map.plus(key, newValue);
        if (newMap == map || changeUserMap(map, newMap)) {
          return true;
        }
      }
    }
  }

  @Override
  public @NotNull <T> T putUserDataIfAbsent(final @NotNull Key<T> key, final @NotNull T value) {
    ExternalUserDataStorage external = externalStorage();
    if (external != null) {
      return external.putUserDataIfAbsent(this, key, value);
    }
    else {
      while (true) {
        KeyFMap map = getUserMap();
        T oldValue = map.get(key);
        if (oldValue != null) {
          return oldValue;
        }
        KeyFMap newMap = map.plus(key, value);
        if (newMap == map || changeUserMap(map, newMap)) {
          return value;
        }
      }
    }
  }

  public void copyCopyableDataTo(@NotNull UserDataHolderBase clone) {
    clone.putUserData(COPYABLE_USER_MAP_KEY, getUserData(COPYABLE_USER_MAP_KEY));
  }

  @ApiStatus.Experimental
  public boolean isCopyableDataEqual(@NotNull UserDataHolderBase other) {
    return Objects.equals(getUserData(COPYABLE_USER_MAP_KEY), other.getUserData(COPYABLE_USER_MAP_KEY));
  }

  protected void clearUserData() {
    setUserMap(KeyFMap.EMPTY_MAP);
  }

  protected void setUserMap(@NotNull KeyFMap map) {
    ExternalUserDataStorage external = externalStorage();
    if (external != null) {
      external.setUserMap(this, map);
    }
    else {
      value = map;
    }
  }

  public boolean isUserDataEmpty() {
    return getUserMap().isEmpty();
  }

  @Override
  public String toString() {
    return getUserMap().toString();
  }
}
