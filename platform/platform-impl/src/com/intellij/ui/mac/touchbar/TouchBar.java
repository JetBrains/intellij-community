// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class TouchBar implements NSTLibrary.ItemCreator {
  static final Logger LOG = Logger.getInstance(TouchBar.class);

  protected final @NotNull String myName;    // just for logging/debugging
  protected ID myNativePeer;        // java wrapper holds native object
  protected long myCounter = 0;
  protected final List<TBItem> myItems = new ArrayList<>();
  protected final boolean myReleaseOnClose;

  protected final TBItemButton myCustomEsc;

  public static final TouchBar EMPTY = new TouchBar();

  private TouchBar() {
    myName = "EMPTY_STUB_TOUCHBAR";
    myCustomEsc = null;
    myNativePeer = ID.NIL;
    myReleaseOnClose = false;
  }

  public TouchBar(@NotNull String touchbarName, boolean replaceEsc) {
    this(touchbarName, replaceEsc, false);
  }

  public TouchBar(@NotNull String touchbarName, boolean replaceEsc, boolean releaseOnClose) {
    myName = touchbarName;
    myCustomEsc = replaceEsc ? new TBItemButton(genNewID("esc"), AllIcons.Actions.Cancel, null, this::_closeSelf) : null;
    myNativePeer = NST.createTouchBar(touchbarName, this, myCustomEsc != null ? myCustomEsc.myUid : null);
    myReleaseOnClose = releaseOnClose;
  }

  public boolean isManualClose() { return myCustomEsc != null; }

  @Override
  public String toString() { return myName + "_" + myNativePeer; }

  @Override
  public ID createItem(@NotNull String uid) { // called from AppKit (when NSTouchBarDelegate create items)
    if (myCustomEsc != null && myCustomEsc.myUid.equals(uid))
      return myCustomEsc.getNativePeer();

    TBItem item = findItem(uid);
    if (item == null) {
      LOG.error("can't find TBItem with uid '%s'", uid);
      return ID.NIL;
    }
    // System.out.println("create native peer for item '" + uid + "'");
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
  // NOTE: must call 'selectVisibleItemsToShow' after touchbar filling
  //

  public TBItemButton addButton() {
    final TBItemButton butt = new TBItemButton(genNewID("button"), null, null, null);
    myItems.add(butt);
    return butt;
  }

  public TBItemButton addButton(Icon icon, String text, NSTLibrary.Action action) {
    final TBItemButton butt = new TBItemButton(genNewID("button"), icon, text, action);
    myItems.add(butt);
    return butt;
  }

  public TBItemButton addButton(Icon icon, String text, NSTLibrary.Action action, int buttonFlags) {
    final TBItemButton butt = new TBItemButton(genNewID("button"), icon, text, action, -1, buttonFlags);
    myItems.add(butt);
    return butt;
  }

  public TBItemButton addButton(Icon icon, String text, String actionId) {
    final TBItemButton butt = new TBItemButton(genNewID("button"), icon, text, new PlatformAction(actionId));
    myItems.add(butt);
    return butt;
  }

  public TBItemButton addButton(Icon icon, String text, AnAction act) {
    final TBItemButton butt = new TBItemButton(genNewID("button"), icon, text, new PlatformAction(act));
    myItems.add(butt);
    return butt;
  }

  public TBItemGroup addGroup(List<TBItem> items) {
    final TBItemGroup group = new TBItemGroup(genNewID("group"), items);
    myItems.add(group);
    return group;
  }

  public TBItemPopover addPopover(Icon icon, String text, int width, TouchBar expandTB, TouchBar tapAndHoldTB) {
    final TBItemPopover popover = new TBItemPopover(genNewID("popover"), icon, text, width, expandTB, tapAndHoldTB);
    myItems.add(popover);
    return popover;
  }

  public TBItemScrubber addScrubber(int width) {
    final TBItemScrubber scrubber = new TBItemScrubber(genNewID("scrubber"), width);
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

  public void setPrincipal(@NotNull TBItem item) { NST.setPrincipal(myNativePeer, item.myUid); }

  public void onBeforeShow() {}
  public void onHide() {
    if (myReleaseOnClose)
      release();
  }

  String genNewID(String desc) { return String.format("%s.%s.%d", myName, desc, myCounter++); }

  void forEach(Consumer<? super TBItem> proc) {
    myItems.forEach((item -> {
      if (item instanceof TBItemGroup) {
        ((TBItemGroup)item).getGroupItems().forEach(proc);
        return;
      }
      proc.accept(item);
    }));
  }

  private TBItem findItem(String uid) {
    for (TBItem item : myItems)
      if (item.myUid.equals(uid))
        return item;
    return null;
  }

  private void _closeSelf() { TouchBarsManager.closeTouchBar(this, false); }

  private static class SpacingItem extends TBItem {
    SpacingItem(@NotNull String uid) { super(uid); }
    @Override
    protected void _updateNativePeer() {} // mustn't be called
    @Override
    protected ID _createNativePeer() { return null; } // mustn't be called
  }
}

