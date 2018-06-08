// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

class TBItemPopover extends TBItem {
  private Icon myIcon;
  private String myText;
  private int myWidthPix;
  private TouchBar myExpandTB;
  private TouchBar myTapAndHoldTB;

  // NOTE: make popover with 'flexible' width when widthInPix <= 0
  TBItemPopover(@NotNull String uid, @Nullable ItemListener listener, Icon icon, String text, int widthInPix, TouchBar expandTB, TouchBar tapAndHoldTB) {
    super(uid, listener);
    myIcon = icon;
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

  // NOTE: popover is immutable (at this moment) => update doesn't called => _create/_update can be unsyncronized

  @Override
  protected void _updateNativePeer() {
    NST.updatePopover(myNativePeer, myWidthPix, myText, myIcon, getNativePeer(myExpandTB), getNativePeer(myTapAndHoldTB));
  }

  @Override
  protected ID _createNativePeer() {
    return NST.createPopover(myUid, myWidthPix, myText, myIcon, getNativePeer(myExpandTB), getNativePeer(myTapAndHoldTB));
  }

  private static ID getNativePeer(TouchBar tb) { return tb == null ? ID.NIL : tb.getNativePeer(); }
}
