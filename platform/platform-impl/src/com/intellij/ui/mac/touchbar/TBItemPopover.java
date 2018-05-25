// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class TBItemPopover extends TBItem {
  private Icon myIcon;
  private String myText;
  private int myWidthPix;
  private TouchBar myExpandTB;
  private TouchBar myTapAndHoldTB;

  // NOTE: make popover with 'flexible' width when widthInPix <= 0
  public TBItemPopover(@NotNull String uid, Icon icon, String text, int widthInPix, TouchBar expandTB, TouchBar tapAndHoldTB) {
    super(uid);
    myIcon = icon;
    myText = text;
    myWidthPix = widthInPix;
    myExpandTB = expandTB;
    myTapAndHoldTB = tapAndHoldTB;
  }

  synchronized public void update(Icon icon, String text) {
    myIcon = icon;
    myText = text;
    updateNativePeer();
  }

  public void dismiss() {
    if (myNativePeer == ID.NIL)
      return;

    Foundation.invoke(myNativePeer, "dismissPopover:", ID.NIL);
  }

  @Override
  protected void _releaseChildBars() {
    // called from EDT
    if (myExpandTB != null)
      myExpandTB.release();
    if (myTapAndHoldTB != null)
      myTapAndHoldTB.release();

    myExpandTB = null;
    myTapAndHoldTB = null;
  }

  @Override
  protected void _updateNativePeer() {
    NST.updatePopover(myNativePeer, myWidthPix, myText, myIcon, getNativePeer(myExpandTB), getNativePeer(myTapAndHoldTB));
  }

  @Override
  synchronized protected ID _createNativePeer() {
    return NST.createPopover(myUid, myWidthPix, myText, myIcon, getNativePeer(myExpandTB), getNativePeer(myTapAndHoldTB));
  }

  private static ID getNativePeer(TouchBar tb) { return tb == null ? ID.NIL : tb.getNativePeer(); }
}
