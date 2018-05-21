// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class TBItemGroup extends TBItem {
  private final List<TBItem> myGroupItems = new ArrayList<>();

  TBItemGroup(@NotNull String uid, @NotNull List<TBItem> items) {
    super(uid);
    myGroupItems.addAll(items);
  }

  List<TBItem> getGroupItems() { return myGroupItems; }

  @Override
  protected void _updateNativePeer() { myGroupItems.forEach(item->item._updateNativePeer()); }

  @Override
  protected ID _createNativePeer() {
    if (myGroupItems.isEmpty())
      return ID.NIL;

    final ID[] ids = myGroupItems.stream().map(item->item.getNativePeer()).toArray(size -> new ID[size]);
    return NST.createGroupItem(myUid, ids, ids.length);
  }
}
