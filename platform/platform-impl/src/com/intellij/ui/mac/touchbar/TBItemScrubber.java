// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ui.mac.foundation.ID;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

public class TBItemScrubber extends TBItem {
  private final int myWidth;
  private List<ItemData> myItems;

  // NOTE: make scrubber with 'flexible' width when scrubWidth <= 0
  public TBItemScrubber(@NotNull String uid, int scrubWidth) {
    super(uid);
    myWidth = scrubWidth;
  }

  synchronized public void setItems(List<ItemData> items) {
    myItems = items;
    updateNativePeer();
  }

  @Override
  protected void _updateNativePeer() {
    final NSTLibrary.ScrubberItemData[] vals = makeItemsArray();
    NST.updateScrubber(myNativePeer, myWidth, vals, vals != null ? vals.length : 0);
    releaseItemsMem();
  }

  @Override
  synchronized protected ID _createNativePeer() {
    final NSTLibrary.ScrubberItemData[] vals = makeItemsArray();
    final ID result = NST.createScrubber(myUid, myWidth, vals, vals != null ? vals.length : 0);
    releaseItemsMem();
    return result;
  }

  private NSTLibrary.ScrubberItemData[] makeItemsArray() {
    if (myItems == null)
      return null;

    final NSTLibrary.ScrubberItemData scitem = new NSTLibrary.ScrubberItemData();
    // Structure.toArray allocates a contiguous block of memory internally (each array item is inside this block)
    // note that for large arrays, this can be extremely slow
    final NSTLibrary.ScrubberItemData[] vals = (NSTLibrary.ScrubberItemData[])scitem.toArray(myItems.size());
    int c = 0;
    for (ItemData id : myItems)
      id.fill(vals[c++]);

    return vals;
  }

  private void releaseItemsMem() {
    if (myItems == null)
      return;

    for (ItemData id: myItems)
      id.releaseMem();
  }

  static class ItemData {
    final Icon myIcon;
    final String myText;
    final NSTLibrary.Action myAction;

    private Memory myIconMem; // NOTE: must hold the memory to prevent dealloc until native caller of 'fill' finished his job
    private Memory myTextMem;

    ItemData(Icon icon, String text, NSTLibrary.Action action) {
      this.myIcon = icon;
      this.myText = text;
      this.myAction = action;
    }

    void fill(@NotNull NSTLibrary.ScrubberItemData out) {
      if (myText != null) {
        final byte[] data = Native.toByteArray(myText, "UTF8");
        myTextMem = new Memory(data.length + 1);
        myTextMem.write(0, data, 0, data.length);
        myTextMem.setByte(data.length, (byte)0);
      } else
        myTextMem = null;

      out.text = myTextMem;

      if (myIcon != null) {
        final int len = myIcon.getIconWidth()*myIcon.getIconHeight()*4;
        myIconMem = new Memory(len);
        myIconMem.write(0, getRaster(myIcon), 0, len);

        out.rasterW = myIcon.getIconWidth();
        out.rasterH = myIcon.getIconHeight();
      } else {
        myIconMem = null;
        out.rasterW = 0;
        out.rasterH = 0;
      }

      out.raster4ByteRGBA = myIconMem;
      out.action = myAction;
    }

    void releaseMem() {
      myIconMem = null;
      myTextMem = null;
    }
  }
}
