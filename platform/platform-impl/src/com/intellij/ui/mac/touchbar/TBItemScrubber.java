// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class TBItemScrubber extends TBItem implements NSTLibrary.ScrubberDelegate {
  private final int myWidth;
  private final @Nullable TouchBarStats myStats;
  private final NSTLibrary.ScrubberCacheUpdater myUpdater;
  private List<ItemData> myItems;
  private int myNativeItemsCount;

  // NOTE: make scrubber with 'flexible' width when scrubWidth <= 0
  TBItemScrubber(@Nullable ItemListener listener, @Nullable TouchBarStats stats, int scrubWidth) {
    super("scrubber", listener);
    myWidth = scrubWidth;
    myStats = stats;
    myUpdater = () -> {
      // NOTE: called from AppKit (when last cached item become visible)
      if (myItems == null || myItems.isEmpty())
        return 0;
      if (myNativeItemsCount >= myItems.size())
        return 0;

      final int chunkSize = 25;
      final int newItemsCount = Math.min(chunkSize, myItems.size() - myNativeItemsCount);
      NST.updateScrubberItems(myNativePeer, myItems, myNativeItemsCount, newItemsCount);
      myNativeItemsCount += newItemsCount;
      return newItemsCount;
    };
  }

  TBItemScrubber addItem(Icon icon, String text, Runnable action) {
    if (myItems == null)
      myItems = new ArrayList<>();
    final Runnable nativeAction = action == null && myListener == null ? null : ()-> {
      if (action != null)
        action.run();
      if (myListener != null)
        myListener.onItemEvent(this, 0);
    };
    if (icon instanceof EmptyIcon)
      icon = null;
    else if (icon != null) {
      final long startNs = myStats != null ? System.nanoTime() : 0;
      icon = IconLoader.getDarkIcon(icon, true);
      if (myStats != null)
        myStats.incrementCounter(StatsCounters.iconLoadingRenderingDurationNs, System.nanoTime() - startNs);
    }
    myItems.add(new ItemData(icon, text, nativeAction));
    return this;
  }

  void enableItems(Collection<Integer> indices, boolean enabled) {
    if (indices == null || indices.isEmpty())
      return;

    for (int c = 0; c < myItems.size(); ++c) {
      if (!indices.contains(c))
        continue;
      final ItemData id = myItems.get(c);
      id.myEnabled = enabled;
    }

    if (myNativePeer == ID.NIL)
      return;

    NST.enableScrubberItems(myNativePeer, indices, enabled);
  }

  void showItems(Collection<Integer> indices, boolean visible, boolean inverseOthers) {
    NST.showScrubberItem(myNativePeer, indices, visible, inverseOthers);
  }

  // NOTE: scrubber is immutable container => update doesn't called => _create/_update can be unsyncronized

  @Override
  protected void _updateNativePeer() {
    Logger.getInstance(TBItemScrubber.class).error("_updateNativePeer of scrubber masn't be called");
  }

  @Override
  protected ID _createNativePeer() {
    myNativeItemsCount = myItems == null || myItems.isEmpty() ? 0 : Math.min(30, myItems.size());
    final ID result = NST.createScrubber(getUid(), myWidth, this, myUpdater, myItems, myNativeItemsCount, myStats);
    NST.enableScrubberItems(result, _getDisabledIndices(), false);
    return result;
  }

  @Override
  public void execute(int itemIndex) {
    if (myItems == null || myItems.isEmpty() || itemIndex < 0 || itemIndex >= myItems.size())
      return;
    final ItemData id = myItems.get(itemIndex);
    if (id != null && id.myAction != null)
      id.myAction.run();
  }

  private List<Integer> _getDisabledIndices() {
    final List<Integer> disabled = new ArrayList<>();
    for (int c = 0; c < myItems.size(); ++c) {
      if (!myItems.get(c).myEnabled)
        disabled.add(c);
    }
    return disabled;
  }

  static class ItemData {
    private byte[] myTextBytes; // cache

    final Icon myIcon;
    final String myText;
    final Runnable myAction;
    final float fMulX;
    boolean myEnabled = true;

    ItemData(Icon icon, String text, Runnable action) {
      this.myIcon = icon;
      this.myText = text;
      this.myAction = action;

      final Application app = ApplicationManager.getApplication();
      fMulX = myIcon == null ? 1.f : (app != null && UISettings.getInstance().getPresentationMode() ? 40.f / myIcon.getIconHeight() : (myIcon.getIconHeight() < 24 ? 40.f / 16 : 44.f / myIcon.getIconHeight()));
    }

    byte[] getTextBytes() {
      if (myTextBytes == null && myText != null)
        try {
          myTextBytes = myText.getBytes("UTF8");
        } catch (UnsupportedEncodingException e) {
          e.printStackTrace();
        }

      return myTextBytes;
    }
  }
}
