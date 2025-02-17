// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac.touchbar;

import com.intellij.ui.mac.foundation.ID;
import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Pointer;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface NSTLibrary extends Library {
  ID createTouchBar(String name, ItemCreator creator, String escId); // if defined escId => replace esc button with custom item
  void setTouchBar(ID nsView, ID tbObj);
  void selectItemsToShow(ID tbObj, String[] ids, int count);
  void setPrincipal(ID tbObj, String uid);

  void releaseNativePeer(ID nativePeerPtr);

  interface Action extends Callback {
    void execute();
  }

  interface ItemCreator extends Callback {
    ID createItem(String uid);
  }

  interface ScrubberDelegate extends Callback {
    void execute(int itemIndex);
  }

  interface ScrubberCacheUpdater extends Callback {
    int update(); // NOTE: called from AppKit when last cached item become visible and we need to update native cache with new items
  }

  // all creators are called from AppKit (when TB becomes visible and asks delegate to create objects) => autorelease objects are owned by default NSAutoReleasePool (of AppKit-thread)
  // creator returns non-autorelease obj to be owned by java-wrapper
  ID createButton(String uid, int buttWidth, int buttonFlags,
                  String text, String hint, int isHintDisabled, Pointer raster4ByteRGBA, int w, int h, Action action);
  ID createScrubber(String uid, int itemWidth, ScrubberDelegate delegate, ScrubberCacheUpdater updater, Pointer packedItems, int byteCount);
  ID createGroupItem(String uid, ID[] items, int count);

  int BUTTON_UPDATE_LAYOUT  = 1;
  int BUTTON_UPDATE_FLAGS   = 1 << 1;
  int BUTTON_UPDATE_TEXT    = 1 << 2;
  int BUTTON_UPDATE_IMG     = 1 << 3;
  int BUTTON_UPDATE_ACTION  = 1 << 4;

  int BUTTON_FLAG_DISABLED  = 1;
  int BUTTON_FLAG_SELECTED  = 1 << 1;
  int BUTTON_FLAG_COLORED   = 1 << 2;
  int BUTTON_FLAG_TOGGLE    = 1 << 3;
  int BUTTON_FLAG_TRANSPARENT_BG = 1 << 4;

  int LAYOUT_WIDTH_MASK       = 0x0FFF;
  int LAYOUT_FLAG_MIN_WIDTH   = 1 << 15;
  int LAYOUT_FLAG_MAX_WIDTH   = 1 << 14;
  int LAYOUT_MARGIN_SHIFT     = 2*8;
  int LAYOUT_MARGIN_MASK      = 0xFF << LAYOUT_MARGIN_SHIFT;
  int LAYOUT_BORDER_SHIFT     = 3*8;
  int LAYOUT_BORDER_MASK      = 0xFF << LAYOUT_BORDER_SHIFT;

  int BUTTON_PRIORITY_SHIFT     = 3*8;
  int BUTTON_PRIORITY_MASK      = 0xFF << BUTTON_PRIORITY_SHIFT;

  // all updaters are called from EDT (when update UI, or from all another threads except AppKit)
  // C-implementation creates NSAutoReleasePool internally
  void updateButton(ID buttonObj, int updateOptions, int buttWidth, int buttonFlags,
                    String text, String hint, int isHintDisabled, Pointer raster4ByteRGBA, int w, int h, Action action);

  void enableScrubberItems(ID scrubObj, Pointer itemIndices, int count, boolean enabled);
  void showScrubberItems(ID scrubObj, Pointer itemIndices, int count, boolean show, boolean inverseOthers);
  void updateScrubberItems(ID scrubObj, Pointer packedItems, int byteCount, int fromIndex);

  void setArrowImage(ID buttObj, Pointer raster4ByteRGBA, int w, int h);

  static int priority2mask(byte prio) { return (prio + 128) << BUTTON_PRIORITY_SHIFT; }
  static int margin2mask(byte margin) { return ((int)margin & 0xFF) << LAYOUT_MARGIN_SHIFT; }
  static int border2mask(byte border) { return ((int)border & 0xFF) << LAYOUT_BORDER_SHIFT; }
}
