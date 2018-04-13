// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

interface BarCreator {
  TouchBar create();
}

public class SingleBarContainer implements BarContainer {
  private final BarCreator myCreator;
  private TouchBar myTouchBar = null;

  public SingleBarContainer(BarCreator creator) { myCreator = creator; }

  @Override
  public TouchBar get() {
    if (myTouchBar == null) {
      try (NSAutoreleaseLock lock = new NSAutoreleaseLock()) {
        myTouchBar = myCreator.create();
      }
    }
    return myTouchBar;
  }

  @Override
  public void release() {
    if (myTouchBar != null) {
      myTouchBar.release();
      myTouchBar = null;
    }
  }
}
