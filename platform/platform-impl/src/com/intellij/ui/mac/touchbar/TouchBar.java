// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class TouchBar implements NSTLibrary.ItemCreator {
  static final Logger LOG = Logger.getInstance(TouchBar.class);
  private static final boolean IS_LOGGING_ENABLED = false;

  protected final @NotNull String myName;    // just for logging/debugging
  protected ID myNativePeer;        // java wrapper holds native object
  protected long myCounter = 0;
  protected final List<TBItem> myItems = new ArrayList<>();

  private BarContainer myContainer = null;

  public TouchBar(@NotNull String touchbarName) {
    myName = touchbarName;
    myNativePeer = NST.createTouchBar(touchbarName, this);
  }

  @Override
  public String toString() { return myName; }

  @Override
  public ID createItem(@NotNull String uid) {
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

  public void closeAndRelease() {
    if (myContainer == null)
      return;

    TouchBarsManager.closeTouchBar(myContainer);
    myContainer = null;
    release();
  }

  public void show() {
    if (myContainer != null)
      return;

    selectVisibleItemsToShow();
    final TouchBar finalThis = this;
    myContainer = new BarContainer() {
      @Override
      public TouchBar get() { return finalThis; }
      @Override
      public void release() { finalThis.release(); }
    };
    TouchBarsManager.showTouchBar(myContainer);
  }

  //
  // NOTE: must call 'selectVisibleItemsToShow' after touchbar filling
  //

  public TBItemButton addButton() {
    final String uid = String.format("%s.button.%d", myName, myCounter++);
    final TBItemButton butt = new TBItemButton(uid, null, null, null);
    myItems.add(butt);
    return butt;
  }

  public TBItemButton addButton(Icon icon, String text, NSTLibrary.Action action) {
    final String uid = String.format("%s.button.%d", myName, myCounter++);
    final TBItemButton butt = new TBItemButton(uid, icon, text, action);
    myItems.add(butt);
    return butt;
  }

  public TBItemButton addButton(Icon icon, String text, String actionId) {
    final String uid = String.format("%s.button.%d", myName, myCounter++);
    final TBItemButton butt = new TBItemButton(uid, icon, text, new PlatformAction(actionId));
    myItems.add(butt);
    return butt;
  }

  public TBItemButton addButton(Icon icon, String text, AnAction act) {
    final String uid = String.format("%s.button.%d", myName, myCounter++);
    final TBItemButton butt = new TBItemButton(uid, icon, text, new PlatformAction(act));
    myItems.add(butt);
    return butt;
  }

  public TBItemPopover addPopover(Icon icon, String text, int width, TouchBar expandTB, TouchBar tapAndHoldTB) {
    final String uid = String.format("%s.popover.%d", myName, myCounter++);
    final TBItemPopover popover = new TBItemPopover(uid, icon, text, width, expandTB, tapAndHoldTB);
    myItems.add(popover);
    return popover;
  }

  public TBItemScrubber addScrubber(int width) {
    final String uid = String.format("%s.scrubber.%d", myName, myCounter++);
    final TBItemScrubber scrubber = new TBItemScrubber(uid, width);
    myItems.add(scrubber);
    return scrubber;
  }

  public TBItemScrubber addScrubber() { return addScrubber(500); }

  public void addSpacing(boolean large) {
    final SpacingItem spacing = new SpacingItem(large ? "static_touchbar_item_large_space" : "static_touchbar_item_small_space");
    myItems.add(spacing);
  }

  public void addFlexibleSpacing() {
    final SpacingItem spacing = new SpacingItem("static_touchbar_item_flexible_space");
    myItems.add(spacing);
  }

  public void selectVisibleItemsToShow() {
    if (myItems.isEmpty())
      return;

    // TODO: cache prev ids and compare with new list
    final String[] ids = new String[myItems.size()];
    int c = 0;
    for (TBItem item : myItems) {
      if (item.isVisible())
        ids[c++] = item.myUid;
    }

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
      LOG.trace("TouchBar [" + myName + "]: " + String.format(fmt, args));
  }

  private static class SpacingItem extends TBItem {
    SpacingItem(@NotNull String uid) { super(uid); }
    @Override
    protected void _updateNativePeer() {} // mustn't be called
    @Override
    protected ID _createNativePeer() { return null; } // mustn't be called
  }
}

