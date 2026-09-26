// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

final class ItemsContainer {
  private final @NotNull String myName;    // just for logging/debugging
  private final @NotNull List<@NotNull TBItem> myItems = new ArrayList<>();

  private long myCounter = 0; // for unique id generation

  ItemsContainer(@NotNull String name) { myName = name; }

  synchronized boolean isEmpty() { return myItems.isEmpty(); }

  synchronized int size() { return myItems.size(); }

  synchronized TBItem get(int index) { return myItems.get(index); }

  @Override
  public String toString() { return myName; }

  synchronized @NotNull String getDescription() {
    if (myItems.isEmpty())
      return "empty_container";
    StringBuilder res = new StringBuilder(String.format("items [%d]: ", myItems.size()));
    for (TBItem item : myItems) {
      res.append(item.getUid());
      res.append(", ");
    }
    return res.toString();
  }

  void addItem(@NotNull TBItem item) {
    addItem(item, -1);
  }

  synchronized void addItem(@NotNull TBItem item, int index) {
    if (item.getUid() == null || item.getUid().isEmpty()) {
      item.setUid(_genNewID(item.getName()));
    }
    if (index >= 0 && index < myItems.size()) {
      myItems.add(index, item);
    }
    else {
      myItems.add(item);
    }
  }

  synchronized void addItem(@NotNull TBItem item, @Nullable TBItem positionAnchor) {
    final int index = positionAnchor != null ? myItems.indexOf(positionAnchor) : -1;
    addItem(item, index);
  }

  void addSpacing(boolean large) {
    addSpacing(large, -1);
  }

  synchronized void addSpacing(boolean large, int index) {
    final SpacingItem spacing = new SpacingItem();
    spacing.setUid(large ? "static_touchbar_item_large_space" : "static_touchbar_item_small_space");
    if (index >= 0 && index < myItems.size()) {
      myItems.add(index, spacing);
    }
    else {
      myItems.add(spacing);
    }
  }

  void addFlexibleSpacing() {
    addFlexibleSpacing(-1);
  }

  synchronized void addFlexibleSpacing(int index) {
    final SpacingItem spacing = new SpacingItem();
    spacing.setUid("static_touchbar_item_flexible_space");
    if (index >= 0 && index < myItems.size()) {
      myItems.add(index, spacing);
    }
    else {
      myItems.add(spacing);
    }
  }

  synchronized void releaseAll() {
    myItems.forEach(item -> item.releaseNativePeer());
    myItems.clear();
  }

  synchronized @NotNull String @NotNull [] getVisibleIds() {
    String[] ids = new String[myItems.size()];
    int c = 0;
    for (TBItem item : myItems) {
      if (item.myIsVisible) {
        ids[c++] = item.getUid();
      }
    }
    return c == myItems.size() ? ids : Arrays.copyOf(ids, c);
  }

  synchronized ID @NotNull [] getNativePeers() {
    final ID[] ids = new ID[myItems.size()];
    int c = 0;
    for (TBItem item : myItems) {
      final ID nativePeer = item.createNativePeer();
      if (!ID.NIL.equals(nativePeer)) {
        ids[c++] = nativePeer;
      }
    }
    return c == myItems.size() ? ids : Arrays.copyOf(ids, c);
  }

  synchronized void softClear(@NotNull Map<AnAction, TBItemAnActionButton> actPool, @NotNull Map<Integer, TBItemGroup> groupPool) {
    myItems.forEach((item -> {
      if (item instanceof TBItemAnActionButton actItem) {
        TBItemAnActionButton prev = actPool.put(actItem.getAnAction(), actItem);
        if (prev != null) { // just for insurance
          prev.releaseNativePeer();
        }
      }
      if (item instanceof TBItemGroup group) {
        TBItemGroup prev = groupPool.put(group.size(), group);
        if (prev != null) { // just for insurance
          prev.releaseNativePeer();
        }
      }
    }));
    myItems.clear();
  }

  synchronized @Nullable TBItem findItem(String uid) {
    for (TBItem item : myItems) {
      final String itemUid = item.getUid();
      if (itemUid != null && itemUid.equals(uid)) {
        return item;
      }
    }
    return null;
  }

  private @NotNull String _genNewID(String desc) { return String.format("%s.%s.%d", myName, desc, myCounter++); }
}
