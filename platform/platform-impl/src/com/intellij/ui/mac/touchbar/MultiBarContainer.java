// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class MultiBarContainer implements BarContainer {
  private final @NotNull TouchBar myMain;
  private final Map<Long, TouchBar> myKeyMask2Alt = new HashMap<>();
  private TouchBar myCurrent;

  MultiBarContainer(@NotNull TouchBar main) { myMain = main; }

  void registerAltByKeyMask(long keyMask, @NotNull TouchBar altBar) { myKeyMask2Alt.put(keyMask, altBar); }

  void selectBarByKeyMask(long keyMask) {
    if (keyMask == 0) {
      myCurrent = myMain;
      return;
    }

    final TouchBar alt = myKeyMask2Alt.get(keyMask);
    if (alt != null)
      myCurrent = alt;
  }

  @Override
  public TouchBar get() {
    if (myCurrent == null)
      myCurrent = myMain;
    return myCurrent;
  }

  @Override
  public void release() {
    myMain.release();
    myKeyMask2Alt.forEach((mask, bar)->bar.release());
  }
}
