// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

final class ItemsContainer {
  private final @NotNull String myName;    // just for logging/debugging
  private final @NotNull List<TBItem> myItems = new ArrayList<>();

  private long myCounter = 0; // for unique id generation

  ItemsContainer(@NotNull @NonNls String name) { myName = name; }

  boolean isEmpty() { return myItems.isEmpty(); }

  boolean hasAnActionItems() { return anyMatchDeep(item -> item instanceof TBItemAnActionButton); }

  @Override
  public String toString() { return myName; }

  void addItem(@NotNull TBItem item) {
    addItem(item, -1);
  }

  void addItem(@NotNull TBItem item, int index) {
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

  void addItem(@NotNull TBItem item, @Nullable TBItem positionAnchor) {
    final int index = positionAnchor != null ? myItems.indexOf(positionAnchor) : -1;
    addItem(item, index);
  }

  void addSpacing(boolean large) {
    addSpacing(large, -1);
  }

  void addSpacing(boolean large, int index) {
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

  void addFlexibleSpacing(int index) {
    final SpacingItem spacing = new SpacingItem();
    spacing.setUid("static_touchbar_item_flexible_space");
    if (index >= 0 && index < myItems.size()) {
      myItems.add(index, spacing);
    }
    else {
      myItems.add(spacing);
    }
  }

  void releaseAll() {
    myItems.forEach(item -> item.releaseNativePeer());
    myItems.clear();
  }

  void remove(@Nullable Predicate<? super TBItem> filter) {
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
        if (group.isEmpty()) {
          removeGroup = true;
        }
      }
      if (removeGroup || filter.test(item)) {
        item.releaseNativePeer();
        i.remove();
      }
    }
  }

  @NotNull String @NotNull [] getVisibleIds() {
    String[] ids = new String[myItems.size()];
    int c = 0;
    for (TBItem item : myItems) {
      if (item.myIsVisible) {
        ids[c++] = item.getUid();
      }
    }
    return c == myItems.size() ? ids : Arrays.copyOf(ids, c);
  }

  ID @NotNull [] getVisibleNativePeers() {
    final ID[] ids = new ID[myItems.size()];
    int c = 0;
    for (TBItem item : myItems) {
      if (item.myIsVisible && !ID.NIL.equals(item.getNativePeer())) {
        ids[c++] = item.getNativePeer();
      }
    }
    return c == myItems.size() ? ids : Arrays.copyOf(ids, c);
  }

  void softClear(@NotNull Map<AnAction, TBItemAnActionButton> actPool, @NotNull LinkedList<TBItemGroup> groupPool) {
    myItems.forEach((item -> {
      if (item instanceof TBItemAnActionButton) {
        final TBItemAnActionButton actItem = (TBItemAnActionButton)item;
        actPool.put(actItem.getAnAction(), actItem);
      }
      if (item instanceof TBItemGroup) {
        final TBItemGroup group = (TBItemGroup)item;
        group.getContainer().softClear(actPool, groupPool);
        groupPool.add(group);
      }
    }));
    myItems.clear();
  }

  TBItem getItem(int index) { return index >= 0 && index < myItems.size() ? myItems.get(index) : null; }

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
      if (item instanceof TBItemGroup) {
        return ((TBItemGroup)item).getContainer().anyMatchDeep(proc);
      }
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
    for (TBItem item : myItems) {
      if (item.getUid().equals(uid)) {
        return item;
      }
    }
    return null;
  }

  private @NotNull String _genNewID(String desc) { return String.format("%s.%s.%d", myName, desc, myCounter++); }
}
