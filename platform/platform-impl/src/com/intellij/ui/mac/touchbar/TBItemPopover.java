// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

class TBItemPopover extends TBItem {
  private final Icon myIcon;
  private final String myText;
  private final int myWidthPix;
  private TouchBar myExpandTB;
  private TouchBar myTapAndHoldTB;

  // NOTE: make popover with 'flexible' width when widthInPix <= 0
  TBItemPopover(@Nullable ItemListener listener, Icon icon, String text, int widthInPix, TouchBar expandTB, TouchBar tapAndHoldTB) {
    super("popover", listener);
    myIcon = icon != null ? IconLoader.getDarkIcon(icon, true) : null;
    myText = text;
    myWidthPix = widthInPix;
    myExpandTB = expandTB;
    myTapAndHoldTB = tapAndHoldTB;
  }

  @Override
  void releaseNativePeer() {
    // called from EDT
    if (myExpandTB != null)
      myExpandTB.release();
    if (myTapAndHoldTB != null)
      myTapAndHoldTB.release();

    myExpandTB = null;
    myTapAndHoldTB = null;
    super.releaseNativePeer();
  }

  @Override
  protected ID _createNativePeer() {
    return NST.createPopover(getUid(), myWidthPix, myText, myIcon, getNativePeer(myExpandTB), getNativePeer(myTapAndHoldTB));
  }

  private static ID getNativePeer(TouchBar tb) { return tb == null ? ID.NIL : tb.getNativePeer(); }
}
