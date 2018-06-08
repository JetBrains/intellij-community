// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

class TBItemScrubber extends TBItem {
  private final int myWidth;
  private List<ItemData> myItems;

  // NOTE: make scrubber with 'flexible' width when scrubWidth <= 0
  TBItemScrubber(@NotNull String uid, @Nullable ItemListener listener, int scrubWidth) {
    super(uid, listener);
    myWidth = scrubWidth;
  }

  TBItemScrubber addItem(Icon icon, String text, Runnable action) {
    if (myItems == null)
      myItems = new ArrayList<>();
    final NSTLibrary.Action nativeAction = action == null && myListener == null ? null : ()-> {
      if (action != null)
        ApplicationManager.getApplication().invokeLater(action);
      if (myListener != null)
        myListener.onItemEvent(this, 0);
    };
    myItems.add(new ItemData(icon, text, nativeAction));
    updateNativePeer();
    return this;
  }

  // NOTE: scrubber is immutable (at this moment) => update doesn't called => _create/_update can be unsyncronized

  @Override
  protected void _updateNativePeer() { NST.updateScrubber(myNativePeer, myWidth, myItems); }

  @Override
  protected ID _createNativePeer() { return NST.createScrubber(myUid, myWidth, myItems); }

  static class ItemData {
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
