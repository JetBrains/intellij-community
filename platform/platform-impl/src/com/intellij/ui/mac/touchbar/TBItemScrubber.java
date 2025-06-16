// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac.touchbar;

import com.intellij.ide.ActivityTracker;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@ApiStatus.Internal
public final class TBItemScrubber extends TBItem implements NSTLibrary.ScrubberDelegate {
  private final int myWidth;
  private final @Nullable TouchBarStats myStats;
  private final NSTLibrary.ScrubberCacheUpdater myUpdater;
  private final List<ItemData> myItems = new ArrayList<>();
  private int myNativeItemsCount;

  // NOTE: should be completely filled before native peer creation
  // (now it assumed that updateScrubberItems traverse fixed collection items)
  TBItemScrubber(@Nullable ItemListener listener, @Nullable TouchBarStats stats, int scrubWidth) {
    super("scrubber", listener);
    myWidth = scrubWidth;
    myStats = stats;
    myUpdater = () -> {
      // NOTE: called from AppKit (when last cached item become visible)
      if (myItems.isEmpty())
        return 0;
      if (myNativeItemsCount >= myItems.size())
        return 0;

      final int chunkSize = 25;
      final int newItemsCount = Math.min(chunkSize, myItems.size() - myNativeItemsCount);
      final int fromPosition = myNativeItemsCount;
      NST.updateScrubberItems(this, fromPosition, newItemsCount, false, true);

      final @NotNull Application app = ApplicationManager.getApplication();
      app.executeOnPooledThread(() -> NST.updateScrubberItems(this, fromPosition, newItemsCount, true, false));

      myNativeItemsCount += newItemsCount;
      return newItemsCount;
    };
  }

  @NotNull List<ItemData> getItems() { return myItems; }

  @Nullable TouchBarStats getStats() { return myStats; }

  // NOTE: designed to be completely filled before usage
  @VisibleForTesting
  public TBItemScrubber addItem(Icon icon, String text, Runnable action) {
    final Runnable nativeAction = action == null && myListener == null ? null : ()-> {
      if (action != null)
        action.run();
      if (myListener != null)
        myListener.onItemEvent(this, 0);
      ActivityTracker.getInstance().inc();
    };
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

    synchronized (this) {
      NST.enableScrubberItems(myNativePeer, indices, enabled);
    }
  }

  void showItems(Collection<Integer> indices, boolean visible, boolean inverseOthers) {
    synchronized (this) {
      NST.showScrubberItem(myNativePeer, indices, visible, inverseOthers);
    }
  }

  @Override
  protected ID _createNativePeer() {
    myNativeItemsCount = myItems.isEmpty() ? 0 : Math.min(30, myItems.size());
    final ID result = NST.createScrubber(getUid(), myWidth, this, myUpdater, myItems, myNativeItemsCount, myStats);
    NST.enableScrubberItems(result, _getDisabledIndices(), false);
    if (myNativeItemsCount > 0 && result != ID.NIL) {
      final @NotNull Application app = ApplicationManager.getApplication();
      app.executeOnPooledThread(() -> NST.updateScrubberItems(this, 0, myNativeItemsCount, true, false));
    }
    return result;
  }

  @Override
  public void execute(int itemIndex) {
    if (myItems.isEmpty() || itemIndex < 0 || itemIndex >= myItems.size())
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

  static final class ItemData {
    private byte[] myTextBytes; // cache
    private final Icon myIcon;

    private final String myText;
    private final Runnable myAction;
    private boolean myEnabled = true;

    // cache fields (are filled during packing, just for convenience)
    float fMulX = 0;
    Icon darkIcon = null;
    int scaledWidth = 0;
    int scaledHeight = 0;

    ItemData(Icon icon, String text, Runnable action) {
      this.myIcon = icon;
      this.myText = text;
      this.myAction = action;
    }

    Icon getIcon() { return myIcon; }

    String getText() {
      return myText;
    }

    byte[] getTextBytes() {
      if (myTextBytes == null && myText != null) {
        myTextBytes = myText.getBytes(StandardCharsets.UTF_8);
      }

      return myTextBytes;
    }
  }
}
