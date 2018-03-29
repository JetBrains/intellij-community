// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ui.mac.foundation.ID;

import javax.swing.*;

public class TBItemPopover extends TBItem {
  private final Icon myIcon;
  private final String myText;
  private final int myWidthPix;
  private TouchBar myExpandTB;
  private TouchBar myTapAndHoldTB;

  // NOTE: make popover with 'flexible' width when widthInPix <= 0
  public TBItemPopover(Icon icon, String text, int widthInPix) {
    myIcon = icon;
    myText = text;
    myWidthPix = widthInPix;
  }

  @Override
  protected ID _register(ID tb) {
    return TouchBarManager.getNSTLibrary().registerPopover(
      tb, myText, NSTLibrary.getRasterFromIcon(myIcon),
      myIcon.getIconWidth(), myIcon.getIconHeight(), myWidthPix
    );
  }

  void setExpandTB(TouchBar expandTB) {
    myExpandTB = expandTB;
    TouchBarManager.getNSTLibrary().setPopoverExpandTouchBar(myNativePeer, myExpandTB.getNativePeer());
    TouchBarManager.getNSTLibrary().releaseTouchBar(myExpandTB.getNativePeer());// popover (native wrapper) owns tb now
  }

  void setTapAndHoldTB(TouchBar tapHoldTB) {
    myTapAndHoldTB = tapHoldTB;
    TouchBarManager.getNSTLibrary().setPopoverTapAndHoldTouchBar(myNativePeer, myTapAndHoldTB.getNativePeer());
    TouchBarManager.getNSTLibrary().releaseTouchBar(myTapAndHoldTB.getNativePeer());// popover (native wrapper) owns tb now
  }
}
