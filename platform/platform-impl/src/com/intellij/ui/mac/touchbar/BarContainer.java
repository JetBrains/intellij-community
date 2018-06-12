// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Map;

enum BarType {
  DEFAULT,
  DEBUGGER, // debugger must use context of focused component (for example, to use selected text in the Editor)
  POPUP,
  DIALOG
}

class BarContainer {
  private final @NotNull BarType myType;
  private Map<Long, TouchBar> myKeyMask2Alt;
  private @NotNull TouchBar myMain;
  private TouchBar myCurrent;

  BarContainer(@NotNull BarType type, @NotNull TouchBar main, Map<Long, TouchBar> alts) {
    myMain = main;
    myType = type;
    myKeyMask2Alt = alts;
  }

  void set(@NotNull TouchBar main, Map<Long, TouchBar> alts) {
    myMain = main;
    myKeyMask2Alt = alts;
    myCurrent = null;
  }

  void selectBarByKeyMask(long keyMask) {
    if (keyMask == 0) {
      myCurrent = myMain;
      return;
    }

    final TouchBar alt = myKeyMask2Alt == null ? null : myKeyMask2Alt.get(keyMask);
    if (alt != null)
      myCurrent = alt;
  }

  TouchBar get() {
    if (myCurrent == null)
      myCurrent = myMain;
    return myCurrent;
  }

  @NotNull BarType getType() { return myType; }

  boolean isTemporary() { return myType == BarType.POPUP || myType == BarType.DIALOG; }

  void setComponent(Component component) {
    myMain.setComponent(component);
    if (myKeyMask2Alt != null)
      myKeyMask2Alt.values().forEach(tb -> { tb.setComponent(component); });
  }

  void release() {
    myMain.release();
    if (myKeyMask2Alt != null)
      myKeyMask2Alt.forEach((mask, bar)->bar.release());
    myKeyMask2Alt = null;
    myMain = TouchBar.EMPTY;
  }
}
