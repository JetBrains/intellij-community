// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class TBItemGroup extends TBItem {
  private final ItemsContainer myGroupItems;

  TBItemGroup(@NotNull String uid, @Nullable ItemListener listener) {
    super(uid, listener);
    myGroupItems = new ItemsContainer(uid + "_group", listener);
  }

  ItemsContainer getContainer() { return myGroupItems; }

  @Override
  protected void _updateNativePeer() {
    myGroupItems.forEachDeep(item->item._updateNativePeer());
  }

  @Override
  protected ID _createNativePeer() {
    if (myGroupItems.isEmpty())
      return ID.NIL;

    final ID[] ids = myGroupItems.getVisibleNativePeers();
    return NST.createGroupItem(myUid, ids, ids.length);
  }

  @Override
  void releaseNativePeer() {
    myGroupItems.releaseAll();
    super.releaseNativePeer();
  }
}
