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
  ID createTouchBar(String name, ItemCreator creator, String escId); // if defined escId => replace esc button with custom item
  void releaseTouchBar(ID tbObj);
  void setTouchBar(ID tbObj);
  void selectItemsToShow(ID tbObj, String[] ids, int count);
  void setPrincipal(ID tbObj, String uid);

  interface Action extends Callback {
    void execute();
  }

  class ScrubberItemData extends Structure {
    @Override
    protected List<String> getFieldOrder() { return Arrays.asList("text", "raster4ByteRGBA", "rasterW", "rasterH", "action"); }

    public static class ByRef extends ScrubberItemData implements Structure.ByReference {
      public ByRef() {}
    }

    public Pointer text;
    public Pointer raster4ByteRGBA;
    public int rasterW;
    public int rasterH;
    public Action action;
  }

  interface ItemCreator extends Callback {
    ID createItem(String uid);
  }

  // all creators are called from AppKit (when TB becomes visible and asks delegate to create objects) => autorelease objects are owned by default NSAutoReleasePool (of AppKit-thread)
  // creator returns non-autorelease obj to be owned by java-wrapper
  ID createButton(String uid, int buttWidth, int buttonFlags, String text, byte[] raster4ByteRGBA, int w, int h, Action action);
  ID createPopover(String uid, int itemWidth, String text, byte[] raster4ByteRGBA, int w, int h, ID tbObjExpand, ID tbObjTapAndHold);
  ID createScrubber(String uid, int itemWidth, ScrubberItemData[] items, int count);
  ID createGroupItem(String uid, ID[] items, int count);

  int BUTTON_UPDATE_WIDTH   = 1;
  int BUTTON_UPDATE_FLAGS   = 1 << 1;
  int BUTTON_UPDATE_TEXT    = 1 << 2;
  int BUTTON_UPDATE_IMG     = 1 << 3;
  int BUTTON_UPDATE_ACTION  = 1 << 4;
  int BUTTON_UPDATE_ALL     = ~0;

  int BUTTON_FLAG_DISABLED  = 1;
  int BUTTON_FLAG_SELECTED  = 1 << 1;
  int BUTTON_FLAG_COLORED   = 1 << 2;

  // all updaters are called from EDT (when update UI, or from all another threads except AppKit)
  // C-implementation creates NSAutoReleasePool internally
  void updateButton(ID buttonObj, int updateOptions, int buttWidth, int buttonFlags, String text, byte[] raster4ByteRGBA, int w, int h, Action action);
  void updatePopover(ID popoverObj, int itemWidth, String text, byte[] raster4ByteRGBA, int w, int h, ID tbObjExpand, ID tbObjTapAndHold);
  void updateScrubber(ID scrubObj, int itemWidth, ScrubberItemData[] items, int count);
}
