// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;

import static com.intellij.reference.SoftReference.dereference;

public final class LastComputedIconCache {
  private static final Key<Reference<Int2ObjectMap<Icon>>> LAST_COMPUTED_ICON = Key.create("lastComputedIcon");

  public static @Nullable Icon get(@NotNull UserDataHolder holder, int flags) {
    Int2ObjectMap<Icon> map = dereference(holder.getUserData(LAST_COMPUTED_ICON));
    return map == null ? null : map.get(flags);
  }

  public static void put(@NotNull UserDataHolder holder, Icon icon, int flags) {
    Reference<Int2ObjectMap<Icon>> ref = holder.getUserData(LAST_COMPUTED_ICON);
    Int2ObjectMap<Icon> map = dereference(ref);
    if (icon == null) {
      if (map == null) {
        return;
      }
    }
    else {
      while (map == null) {
        Int2ObjectMap<Icon> freshMap = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());
        if (((UserDataHolderEx)holder).replace(LAST_COMPUTED_ICON, ref, new SoftReference<>(freshMap))) {
          map = freshMap;
        }
        else {
          map = dereference(holder.getUserData(LAST_COMPUTED_ICON));
        }
      }
    }
    if (icon == null) {
      map.remove(flags);
    }
    else {
      map.put(flags, icon);
    }
  }

  @TestOnly
  public static void clear(@NotNull UserDataHolder holder) {
    holder.putUserData(LAST_COMPUTED_ICON, null);
  }
}
