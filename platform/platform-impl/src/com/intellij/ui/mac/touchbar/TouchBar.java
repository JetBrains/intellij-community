// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class TouchBar {
  private final String myTbName;
  private final ID myNativePeer;
  private final List<TBItem> myItems = new ArrayList<>();

  TouchBar(String touchbarName) {
    myTbName = touchbarName;
    myNativePeer = TouchBarManager.getNSTLibrary().createTouchBar(touchbarName);
  }

  ID getNativePeer() { return myNativePeer; }

  void release() { TouchBarManager.getNSTLibrary().releaseTouchBar(myNativePeer); }

  // NOTE: must call 'selectAllItemsToShow' after filling
  void addItem(@NotNull TBItem tbi) {
    tbi.register(myNativePeer);
    myItems.add(tbi);
  }

  void selectAllItemsToShow() {
    if (myItems.isEmpty())
      return;

    TouchBarManager.getNSTLibrary().selectAllItemsToShow(myNativePeer);
  }
}
