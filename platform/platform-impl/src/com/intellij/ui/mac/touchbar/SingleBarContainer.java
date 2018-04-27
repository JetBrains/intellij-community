// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

public class SingleBarContainer implements BarContainer {
  private final TouchBar myTouchBar;

  public SingleBarContainer(TouchBar touchBar) { myTouchBar = touchBar; }

  @Override
  public TouchBar get() { return myTouchBar; }
  @Override
  public void release() { myTouchBar.release(); }
}
