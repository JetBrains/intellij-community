// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ui.mac.foundation.ID;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class TBItemScrubber extends TBItem {
  private final List<ItemData> myItems = new ArrayList<>();
  private final int myWidth;

  private final NSTLibrary.ScrubberItemsSource mySource;
  private final NSTLibrary.ScrubberItemsCount myCount;
  private final NSTLibrary.ActionWithIndex myActions;

  // NOTE: make scrubber with 'flexible' width when scrubWidth <= 0
  public TBItemScrubber(@NotNull String uid, int scrubWidth) {
    super(uid);
    myWidth = scrubWidth;

    // NOTE: don't mix JNA-interfaces in one class (causes broken JNA mapping)
    // JNA docs says:
    // Any derived interfaces must define a single public method (which may not be named "hashCode", "equals", or "toString"), or one public method named "callback".
    mySource = (int index, NSTLibrary.ScrubberItemData.ByRef out) -> { return requestScrubberItem(index, out); };
    myCount = () -> { return myItems.size(); };
    myActions = (int index) -> { executeAt(index); };
  }

  public void addItem(Icon icon, String text, NSTLibrary.Action action) {
    myItems.add(new ItemData(icon, text, action));
  }

  @Override
  protected void _updateNativePeer() {
    // TODO: implement
  }

  @Override
  protected ID _createNativePeer() {
    return TouchBarManager.getNSTLibrary().createScrubber(myUid, myWidth, mySource, myCount, myActions);
  }

  public void executeAt(int index) {
    final ItemData id = getItemData(index);
    final NSTLibrary.Action act = (id != null ? id.myAction : null);
    if (act != null)
      act.execute();
  }

  public int requestScrubberItem(int index, @NotNull NSTLibrary.ScrubberItemData.ByRef out) {
    final ItemData id = getItemData(index);
    if (id == null)
      return 1;

    id.fill(out);
    return 0;
  }

  private ItemData getItemData(int index) { return index >= myItems.size() ? null : myItems.get(index); }

  private static class ItemData {
    final Icon myIcon;
    final String myText;
    final NSTLibrary.Action myAction;

    private Memory myIconMem; // NOTE: must hold the memory to prevent dealloc until native caller of 'fill' finised his job
    private Memory myTextMem; // TODO: make cleanup-callback (from native to java) or pass data from java to native (JNA will release temp objects after C-call finished)

    public ItemData(Icon icon, String text, NSTLibrary.Action action) {
      this.myIcon = icon;
      this.myText = text;
      this.myAction = action;
    }

    void fill(@NotNull NSTLibrary.ScrubberItemData.ByRef out) {
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
    }
  }
}
