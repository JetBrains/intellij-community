// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class TBItemButton extends TBItem {
  protected @Nullable NSTLibrary.Action myAction;
  protected @Nullable Icon myIcon;
  protected @Nullable String myText;
  protected int myWidth;

  protected TBItemButton(@NotNull String uid) { this(uid, null, null, null, -1); }

  TBItemButton(@NotNull String uid, Icon icon, String text, NSTLibrary.Action action) {
    this(uid, icon, text, action, -1);
  }

  TBItemButton(@NotNull String uid, Icon icon, String text, NSTLibrary.Action action, int buttWidth) {
    super(uid);
    myAction = action;
    myIcon = icon;
    myText = text;
    myWidth = buttWidth;
  }

  void update(Icon icon, String text, NSTLibrary.Action action) {
    update(icon, text, action, myWidth);
  }
  void update(Icon icon, String text) {
    update(icon, text, myAction, myWidth);
  }

  synchronized private void update(Icon icon, String text, NSTLibrary.Action action, int buttWidth) {
    myIcon = icon;
    myText = text;
    myAction = action;
    myWidth = buttWidth;
    updateNativePeer();
  }

  @Override
  protected void _updateNativePeer() {
    NST.updateButton(myNativePeer, myWidth, myText, getRaster(myIcon), getIconW(myIcon), getIconH(myIcon), myAction);
  }

  @Override
  synchronized protected ID _createNativePeer() {
    return NST.createButton(myUid, myWidth, myText, getRaster(myIcon), getIconW(myIcon), getIconH(myIcon), myAction);
  }
}
