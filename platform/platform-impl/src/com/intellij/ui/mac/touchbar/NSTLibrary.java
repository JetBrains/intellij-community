// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ui.mac.foundation.ID;
import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import java.util.Arrays;
import java.util.List;

public interface NSTLibrary extends Library {
  ID createTouchBar(String name, ItemCreator creator);
  void releaseTouchBar(ID tbObj);
  void setTouchBar(ID tbObj);
  void selectItemsToShow(ID tbObj, String[] ids, int count);

  interface Action extends Callback {
    void execute();
  }
  interface ActionWithIndex extends Callback {
    void executeAt(int index);
  }

  class ScrubberItemData extends Structure {
    @Override
    protected List<String> getFieldOrder() { return Arrays.asList("text", "raster4ByteRGBA", "rasterW", "rasterH"); }

    public static class ByRef extends ScrubberItemData implements Structure.ByReference {
      public ByRef() {}
    }

    public Pointer text;
    public Pointer raster4ByteRGBA;
    public int rasterW;
    public int rasterH;
  }

  interface ItemCreator extends Callback {
    ID createItem(String uid);
  }
  interface ScrubberItemsSource extends Callback {
    int requestScrubberItem(int index, ScrubberItemData.ByRef out);
  }
  interface ScrubberItemsCount extends Callback {
    int getScrubberItemsCount();
  }

  // all creators are called from AppKit (when TB becomes visible and asks delegate to create objects) => autorelease objects are owned by default NSAutoReleasePool (of AppKit-thread)
  // creator returns non-autorelease obj to be owned by java-wrapper
  ID createButton(String uid, int buttWidth, String text, byte[] raster4ByteRGBA, int w, int h, Action action);
  ID createPopover(String uid, int itemWidth, String text, byte[] raster4ByteRGBA, int w, int h, ID tbObjExpand, ID tbObjTapAndHold);
  ID createScrubber(String uid, int itemWidth, ScrubberItemsSource source, ScrubberItemsCount count, ActionWithIndex actions);

  // all updaters are called from EDT (when update UI)
  // must be enclosed with NSAutoReleasePool
  void updateButton(ID buttonObj, int buttWidth, String text, byte[] raster4ByteRGBA, int w, int h, Action action);
  void updatePopover(ID popoverObj, int itemWidth, String text, byte[] raster4ByteRGBA, int w, int h, ID tbObjExpand, ID tbObjTapAndHold);
}
