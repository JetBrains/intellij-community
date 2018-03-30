// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class TBItemButton extends TBItem {
  private @Nullable NSTLibrary.Action myAction;
  private @Nullable Icon myIcon;
  private @Nullable String myText;
  private int myWidth;

  public TBItemButton(@NotNull String uid, Icon icon, String text, NSTLibrary.Action action, int buttWidth) {
    super(uid);
    myAction = action;
    myIcon = icon;
    myText = text;
    myWidth = buttWidth;
  }
  public TBItemButton(@NotNull String uid, Icon icon, String text, NSTLibrary.Action action) {
    this(uid, icon, text, action, -1);
  }

  public void update(Icon icon, String text, NSTLibrary.Action action) {
    myIcon = icon;
    myText = text;
    myAction = action;
    updateNativePeer();
  }

  @Override
  protected void _updateNativePeer() {
    NST.updateButton(myNativePeer, myWidth, myText, getRaster(myIcon), getIconW(myIcon), getIconH(myIcon), myAction);
  }

  @Override
  protected ID _createNativePeer() {
    return NST.createButton(myUid, myWidth, myText, getRaster(myIcon), getIconW(myIcon), getIconH(myIcon), myAction);
  }
}
