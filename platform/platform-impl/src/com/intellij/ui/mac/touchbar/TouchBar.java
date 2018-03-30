// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class TouchBar implements NSTLibrary.ItemCreator {
  private final static boolean IS_LOGGING_ENABLED = false;

  private final String myName;    // just for logging/debugging
  private ID myNativePeer;        // java wrapper holds native object
  private long myCounter = 0;
  private final List<TBItem> myItems = new ArrayList<>();

  TouchBar(String touchbarName) {
    myName = touchbarName;
    myNativePeer = NST.createTouchBar(touchbarName, this);
  }

  @Override
  public ID createItem(String uid) {
    TBItem item = findItem(uid);
    if (item == null) {
      trace("can't find TBItem with uid '%s'", uid);
      return ID.NIL;
    }
    trace("create native peer for item '%s'", uid);
    return item.getNativePeer();
  }

  ID getNativePeer() { return myNativePeer; }

  void release() {
    for (TBItem item : myItems)
      item.releaseNativePeer();
    NST.releaseTouchBar(myNativePeer);

    myItems.clear();
    myNativePeer = ID.NIL;
  }

  //
  // NOTE: must call 'selectAllItemsToShow' after touchbar filling
  //

  TBItemButton addButton(Icon icon, String text, NSTLibrary.Action action) {
    final String uid = String.format("%s.button.%d", myName, myCounter++);
    final TBItemButton butt = new TBItemButton(uid, icon, text, action);
    myItems.add(butt);
    return butt;
  }

  TBItemButton addButton(Icon icon, String text, String actionId) {
    final String uid = String.format("%s.button.%d", myName, myCounter++);
    final TBItemButton butt = new TBItemButton(uid, icon, text, new PlatformAction(actionId));
    myItems.add(butt);
    return butt;
  }

  TBItemPopover addPopover(Icon icon, String text, int width, TouchBar expandTB, TouchBar tapAndHoldTB) {
    final String uid = String.format("%s.popover.%d", myName, myCounter++);
    final TBItemPopover popover = new TBItemPopover(uid, icon, text, width, expandTB, tapAndHoldTB);
    myItems.add(popover);
    return popover;
  }

  TBItemScrubber addScrubber(int width) {
    final String uid = String.format("%s.scrubber.%d", myName, myCounter++);
    final TBItemScrubber scrubber = new TBItemScrubber(uid, width);
    myItems.add(scrubber);
    return scrubber;
  }

  void addSpacing(boolean large) {
    final SpacingItem spacing = new SpacingItem(large ? "static_touchbar_item_large_space" : "static_touchbar_item_small_space");
    myItems.add(spacing);
  }

  void addFlexibleSpacing() {
    final SpacingItem spacing = new SpacingItem("static_touchbar_item_flexible_space");
    myItems.add(spacing);
  }

  void selectAllItemsToShow() {
    if (myItems.isEmpty())
      return;

    final String[] ids = new String[myItems.size()];
    int c = 0;
    for (TBItem item : myItems)
      ids[c++] = item.myUid;

    NST.selectItemsToShow(myNativePeer, ids, ids.length);
  }

  private TBItem findItem(String uid) {
    for (TBItem item : myItems)
      if (item.myUid.equals(uid))
        return item;
    return null;
  }

  private void trace(String fmt, Object... args) {
    if (IS_LOGGING_ENABLED)
      System.out.println("TouchBar [" + myName + "]: " + String.format(fmt, args));
  }

  private static class SpacingItem extends TBItem {
    SpacingItem(@NotNull String uid) { super(uid); }
    @Override
    protected void _updateNativePeer() {} // mustn't be called
    @Override
    protected ID _createNativePeer() { return null; } // mustn't be called
  }
}

