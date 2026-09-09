// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac.touchbar;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Pointer;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface NSTLibrary extends Library {
  Pointer createTouchBar(String name, ItemCreator creator, String escId); // if defined escId => replace esc button with custom item

  void setTouchBar(Pointer nsView, Pointer tbObj);

  void selectItemsToShow(Pointer tbObj, String[] ids, int count);

  void setPrincipal(Pointer tbObj, String uid);

  void releaseNativePeer(Pointer nativePeerPtr);

  interface Action extends Callback {
    void execute();
  }

  interface ItemCreator extends Callback {
    Pointer createItem(String uid);
  }

  interface ScrubberDelegate extends Callback {
    void execute(int itemIndex);
  }

  interface ScrubberCacheUpdater extends Callback {
    int update(); // NOTE: called from AppKit when last cached item become visible and we need to update native cache with new items
  }

  // all creators are called from AppKit (when TB becomes visible and asks delegate to create objects) => autorelease objects are owned by default NSAutoReleasePool (of AppKit-thread)
  // creator returns non-autorelease obj to be owned by java-wrapper
  Pointer createButton(String uid, int buttWidth, int buttonFlags,
                       String text, String hint, int isHintDisabled, Pointer raster4ByteRGBA, int w, int h, Action action);

  Pointer createScrubber(String uid,
                         int itemWidth,
                         ScrubberDelegate delegate,
                         ScrubberCacheUpdater updater,
                         Pointer packedItems,
                         int byteCount);

  Pointer createGroupItem(String uid, Pointer[] items, int count);

  int BUTTON_UPDATE_LAYOUT = 1;
  int BUTTON_UPDATE_FLAGS = 1 << 1;
  int BUTTON_UPDATE_TEXT = 1 << 2;
  int BUTTON_UPDATE_IMG = 1 << 3;
  int BUTTON_UPDATE_ACTION = 1 << 4;

  int BUTTON_FLAG_DISABLED = 1;
  int BUTTON_FLAG_SELECTED = 1 << 1;
  int BUTTON_FLAG_COLORED = 1 << 2;
  int BUTTON_FLAG_TOGGLE = 1 << 3;
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
  void updateButton(Pointer buttonObj, int updateOptions, int buttWidth, int buttonFlags,
                    String text, String hint, int isHintDisabled, Pointer raster4ByteRGBA, int w, int h, Action action);

  void enableScrubberItems(Pointer scrubObj, Pointer itemIndices, int count, boolean enabled);

  void showScrubberItems(Pointer scrubObj, Pointer itemIndices, int count, boolean show, boolean inverseOthers);

  void updateScrubberItems(Pointer scrubObj, Pointer packedItems, int byteCount, int fromIndex);

  void setArrowImage(Pointer buttObj, Pointer raster4ByteRGBA, int w, int h);

  static int priority2mask(byte prio) { return (prio + 128) << BUTTON_PRIORITY_SHIFT; }
  static int margin2mask(byte margin) { return ((int)margin & 0xFF) << LAYOUT_MARGIN_SHIFT; }
  static int border2mask(byte border) { return ((int)border & 0xFF) << LAYOUT_BORDER_SHIFT; }
}
