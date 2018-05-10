// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

public class TBItemScrubber extends TBItem {
  private final int myWidth;
  private List<ItemData> myItems;

  // NOTE: make scrubber with 'flexible' width when scrubWidth <= 0
  public TBItemScrubber(@NotNull String uid, int scrubWidth) {
    super(uid);
    myWidth = scrubWidth;
  }

  synchronized public void setItems(List<ItemData> items) {
    myItems = items;
    updateNativePeer();
  }

  @Override
  protected void _updateNativePeer() { NST.updateScrubber(myNativePeer, myWidth, myItems); }

  @Override
  synchronized protected ID _createNativePeer() { return NST.createScrubber(myUid, myWidth, myItems); }

  public static class ItemData {
    final Icon myIcon;
    final String myText;
    final NSTLibrary.Action myAction;

    public ItemData(Icon icon, String text, NSTLibrary.Action action) {
      this.myIcon = icon;
      this.myText = text;
      this.myAction = action;
    }
  }
}
