// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ui.mac.foundation.ID;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class TBItemScrubber extends TBItem {
  private final List<ItemData> myItems = new ArrayList<>();
  private final int myWidth;

  // NOTE: make scrubber with 'flexible' width when scrubWidth <= 0
  public TBItemScrubber(int scrubWidth) {
    myWidth = scrubWidth;
  }

  public void addItem(Icon icon, String text, NSTLibrary.Action action) {
    myItems.add(new ItemData(icon, text, action));
    TouchBarManager.getNSTLibrary().addScrubberItem(
      myNativePeer, text, NSTLibrary.getRasterFromIcon(icon),
      icon.getIconWidth(), icon.getIconHeight(), action
    );
  }

  @Override
  protected ID _register(ID tbOwner) {
    return TouchBarManager.getNSTLibrary().registerScrubber(tbOwner, myWidth);
  }

  private static class ItemData {
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
