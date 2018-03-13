// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class TouchBar {
  private final String myTbName;
  private final ID myTb;
  private final ID myDelegate;
  private final List<TBItem> myItems = new ArrayList<>();

  TouchBar(String touchbarName) {
    myTbName = touchbarName;
    myTb = Foundation.invoke(Foundation.invoke("NSTouchBar", "alloc"), "init");
    myDelegate = Foundation.invoke(Foundation.invoke("NSTDelegate", "alloc"), "init");
    Foundation.invoke(myTb, "setDelegate:", myDelegate);
  }

  ID getTbID() { return myTb; }

  // NOTE: must call 'flushItems' after filling
  void addItem(@NotNull TBItem tbi) {
    final int index = myItems.size();
    final String uid= String.format("%s.%d",myTbName, index);
    tbi.register(uid);
    myItems.add(tbi);
  }

  void flushItems() {
    if (myItems.isEmpty())
      return;

    Object[] ids = new Object[myItems.size()];
    int c = 0;
    for (TBItem tbi: myItems)
      ids[c++] = Foundation.nsString(tbi.getItemId());

    final ID items = Foundation.invoke("NSArray", "arrayWithObjects:", ids);
    Foundation.invoke(myTb, "setDefaultItemIdentifiers:", items);
  }
}
