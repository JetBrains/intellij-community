// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

class ItemsContainer {
  private final @NotNull String myName;    // just for logging/debugging
  private final @Nullable ItemListener myListener;
  private final @NotNull List<TBItem> myItems = new ArrayList<>();

  private long myCounter = 0; // for unique id generation

  ItemsContainer(@NotNull String name, @Nullable ItemListener listener) { myName = name; myListener = listener; }

  boolean isEmpty() { return myItems.isEmpty(); }
  boolean hasAnActionItems() { return anyMatchDeep(item -> item instanceof TBItemAnActionButton); }

  @Override
  public String toString() { return myName; }

  @NotNull TBItemButton addButton() {
    final TBItemButton butt = new TBItemButton(_genNewID("button"), myListener);
    myItems.add(butt);
    return butt;
  }

  @NotNull TBItemAnActionButton addAnActionButton(@NotNull AnAction act, @Nullable TBItem positionAnchor) {
    final String actId = ApplicationManager.getApplication() != null ? ActionManager.getInstance().getId(act) : act.toString();
    final String uid = String.format("%s.anActionButton.%d.%s", myName, myCounter++, actId);
    final TBItemAnActionButton butt = new TBItemAnActionButton(uid, myListener, act);

    if (positionAnchor != null) {
      final int index = myItems.indexOf(positionAnchor);
      if (index >= 0 && index < myItems.size())
        myItems.add(index, butt);
      else
        myItems.add(butt);
    } else
      myItems.add(butt);

    return butt;
  }

  @NotNull TBItemAnActionButton addAnActionButton(@NotNull AnAction act) {
    return addAnActionButton(act, null);
  }

  @NotNull TBItemGroup addGroup() {
    final TBItemGroup group = new TBItemGroup(_genNewID("group"), myListener);
    myItems.add(group);
    return group;
  }

  @NotNull TBItemPopover addPopover(Icon icon, String text, int width, TouchBar expandTB, TouchBar tapAndHoldTB) {
    final TBItemPopover popover = new TBItemPopover(_genNewID("popover"), myListener, icon, text, width, expandTB, tapAndHoldTB);
    myItems.add(popover);
    return popover;
  }

  @NotNull TBItemScrubber addScrubber() {
    final int defaultScrubberWidth = 500;
    final TBItemScrubber scrubber = new TBItemScrubber(_genNewID("scrubber"), myListener, defaultScrubberWidth);
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

  void releaseAll() {
    myItems.forEach(item -> item.releaseNativePeer());
    myItems.clear();
  }

  void remove(@Nullable Predicate<TBItem> filter) {
    if (filter == null) {
      releaseAll();
      return;
    }

    final Iterator<TBItem> i = myItems.iterator();
    while (i.hasNext()) {
      @NotNull final TBItem item = i.next();
      boolean removeGroup = false;
      if (item instanceof TBItemGroup) {
        final ItemsContainer group = ((TBItemGroup)item).getContainer();
        group.remove(filter);
        if (group.isEmpty())
          removeGroup = true;
      }
      if (removeGroup || filter.test(item)) {
        item.releaseNativePeer();
        i.remove();
      }
    }
  }

  @NotNull String[] getVisibleIds() {
    final String[] ids = new String[myItems.size()];
    int c = 0;
    for (TBItem item : myItems) {
      if (item.myIsVisible)
        ids[c++] = item.myUid;
    }
    return c == myItems.size() ? ids : Arrays.copyOf(ids, c);
  }

  @NotNull ID[] getVisibleNativePeers() {
    final ID[] ids = new ID[myItems.size()];
    int c = 0;
    for (TBItem item : myItems) {
      if (item.myIsVisible && !ID.NIL.equals(item.getNativePeer()))
        ids[c++] = item.getNativePeer();
    }
    return c == myItems.size() ? ids : Arrays.copyOf(ids, c);
  }

  void forEachDeep(Consumer<? super TBItem> proc) {
    myItems.forEach((item -> {
      if (item instanceof TBItemGroup) {
        ((TBItemGroup)item).getContainer().forEachDeep(proc);
        return;
      }
      proc.accept(item);
    }));
  }

  boolean anyMatchDeep(Predicate<? super TBItem> proc) {
    return myItems.stream().anyMatch(item -> {
      if (item instanceof TBItemGroup)
        return ((TBItemGroup)item).getContainer().anyMatchDeep(proc);
      return proc.test(item);
    });
  }

  int releaseItems(Predicate<? super TBItem> proc) {
    Iterator<TBItem> i = myItems.iterator();
    int count = 0;
    while (i.hasNext()) {
      final TBItem tbi = i.next();
      if (proc.test(tbi)) {
        ++count;
        i.remove();
        tbi.releaseNativePeer();
      }
    }
    return count;
  }

  @Nullable
  TBItem findItem(String uid) {
    for (TBItem item : myItems)
      if (item.myUid.equals(uid))
        return item;
    return null;
  }

  private @NotNull String _genNewID(String desc) { return String.format("%s.%s.%d", myName, desc, myCounter++); }
}
