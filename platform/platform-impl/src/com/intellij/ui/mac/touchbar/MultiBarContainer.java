// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class MultiBarContainer implements BarContainer {
  private final @NotNull BarContainer myMain;
  private final Map<Long, BarContainer> myKeyMask2Alt = new HashMap<>();
  private BarContainer myCurrent;

  MultiBarContainer(@NotNull BarContainer main) { myMain = main; }

  void registerAltByKeyMask(long keyMask, @NotNull BarContainer altBar) { myKeyMask2Alt.put(keyMask, altBar); }

  void selectBarByKeyMask(long keyMask) {
    if (keyMask == 0) {
      myCurrent = myMain;
      return;
    }

    // TODO: support composite masks
    final BarContainer alt = myKeyMask2Alt.get(keyMask);
    if (alt != null)
      myCurrent = alt;
  }

  @Override
  public TouchBar get() {
    if (myCurrent == null)
      myCurrent = myMain;
    return myCurrent.get();
  }

  @Override
  public void release() {
    myMain.release();
    myKeyMask2Alt.forEach((mask, bar)->bar.release());
  }
}
