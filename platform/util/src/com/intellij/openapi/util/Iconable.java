// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.reference.SoftReference;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntObjectMap;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface Iconable {
  int ICON_FLAG_VISIBILITY = 0x0001;
  int ICON_FLAG_READ_STATUS = 0x0002;

  Key<Integer> ICON_FLAG_IGNORE_MASK = new Key<>("ICON_FLAG_IGNORE_MASK");

  @MagicConstant(flags = {ICON_FLAG_VISIBILITY, ICON_FLAG_READ_STATUS})
  @interface IconFlags {}

  Icon getIcon(@IconFlags int flags);

  final class LastComputedIcon {
    private static final Key<SoftReference<IntObjectMap<Icon>>> LAST_COMPUTED_ICON = Key.create("lastComputedIcon");

    public static @Nullable Icon get(@NotNull UserDataHolder holder, int flags) {
      IntObjectMap<Icon> map = SoftReference.dereference(holder.getUserData(LAST_COMPUTED_ICON));
      return map == null ? null : map.get(flags);
    }

    public static void put(@NotNull UserDataHolder holder, Icon icon, int flags) {
      SoftReference<IntObjectMap<Icon>> ref = holder.getUserData(LAST_COMPUTED_ICON);
      IntObjectMap<Icon> map = SoftReference.dereference(ref);
      if (icon == null) {
        if (map != null) {
          map.remove(flags);
        }
      }
      else {
        while (map == null) {
          ConcurrentIntObjectMap<Icon> freshMap = ContainerUtil.createConcurrentIntObjectMap();
          if (((UserDataHolderEx) holder).replace(LAST_COMPUTED_ICON, ref, new SoftReference<>(freshMap))) {
            map = freshMap;
          }
          else {
            ref = holder.getUserData(LAST_COMPUTED_ICON);
            map = SoftReference.dereference(ref);
          }
        }
        map.put(flags, icon);
      }
    }
  }
}
