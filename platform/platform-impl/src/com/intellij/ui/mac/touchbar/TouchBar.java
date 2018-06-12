// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

class TouchBar implements NSTLibrary.ItemCreator {
  private static final Logger LOG = Logger.getInstance(TouchBar.class);

  private final ItemsContainer myItems;
  private final ItemListener myItemListener;
  private final boolean myReleaseOnClose;
  private final TBItemButton myCustomEsc;
  private final PresentationFactory myPresentationFactory = new PresentationFactory();

  private ID myNativePeer;        // java wrapper holds native object
  private TimerListener myTimerListener;

  public static final TouchBar EMPTY = new TouchBar();

  private TouchBar() {
    myItems = new ItemsContainer("EMPTY_STUB_TOUCHBAR", null);
    myCustomEsc = null;
    myNativePeer = ID.NIL;
    myReleaseOnClose = false;
    myItemListener = null;
  }

  TouchBar(@NotNull String touchbarName, boolean replaceEsc) {
    this(touchbarName, replaceEsc, false, false);
  }

  TouchBar(@NotNull String touchbarName, boolean replaceEsc, boolean releaseOnClose, boolean autoClose) {
    if (autoClose) {
      myItemListener = (src, evcode) -> {
        // NOTE: called from AppKit thread
        _closeSelf();
      };
    } else
      myItemListener = null;

    myItems = new ItemsContainer(touchbarName, myItemListener);
    if (replaceEsc)
      myCustomEsc = new TBItemButton(touchbarName + "_custom_esc_button", myItemListener).setIcon(AllIcons.Actions.Cancel).setThreadSafeAction(this::_closeSelf);
    else
      myCustomEsc = null;

    myNativePeer = NST.createTouchBar(touchbarName, this, myCustomEsc != null ? myCustomEsc.myUid : null);
    myReleaseOnClose = releaseOnClose;
  }

  static TouchBar buildFromGroup(@NotNull String touchbarName, @NotNull ActionGroup customizedGroup, boolean replaceEsc) {
    final TouchBar result = new TouchBar(touchbarName, replaceEsc);
    BuildUtils.addCustomizedActionGroup(result.myItems, customizedGroup);
    result.selectVisibleItemsToShow();
    return result;
  }

  boolean isManualClose() { return myCustomEsc != null; }
  boolean isEmpty() { return myItems.isEmpty(); }

  @Override
  public String toString() { return myItems.toString() + "_" + myNativePeer; }

  @Override
  public ID createItem(@NotNull String uid) { // called from AppKit (when NSTouchBarDelegate create items)
    if (myCustomEsc != null && myCustomEsc.myUid.equals(uid))
      return myCustomEsc.getNativePeer();

    TBItem item = myItems.findItem(uid);
    if (item == null) {
      LOG.error("can't find TBItem with uid '%s'", uid);
      return ID.NIL;
    }
    // System.out.println("create native peer for item '" + uid + "'");
    return item.getNativePeer();
  }

  ID getNativePeer() { return myNativePeer; }

  void release() {
    myItems.releaseAll();
    if (!myNativePeer.equals(ID.NIL)) {
      NST.releaseTouchBar(myNativePeer);
      myNativePeer = ID.NIL;
    }
    _stopTimer();
  }

  //
  // NOTE: must call 'selectVisibleItemsToShow' after touchbar filling
  //
  @NotNull TBItemButton addButton() { return myItems.addButton(); }
  @NotNull TBItemAnActionButton addAnActionButton(@NotNull AnAction act, boolean hiddenWhenDisabled, int showMode, ModalityState modality) {
    return myItems.addAnActionButton(act, hiddenWhenDisabled, showMode, modality);
  }
  @NotNull TBItemGroup addGroup() { return myItems.addGroup(); }
  @NotNull TBItemScrubber addScrubber() { return myItems.addScrubber(); }
  @NotNull TBItemPopover addPopover(Icon icon, String text, int width, TouchBar expandTB, TouchBar tapAndHoldTB) {
    return myItems.addPopover(icon, text, width, expandTB, tapAndHoldTB);
  }
  @NotNull void addSpacing(boolean large) { myItems.addSpacing(large); }
  @NotNull void addFlexibleSpacing() { myItems.addFlexibleSpacing(); }

  void selectVisibleItemsToShow() {
    if (myItems.isEmpty())
      return;

    final String[] ids = myItems.getVisibleIds();
    NST.selectItemsToShow(myNativePeer, ids, ids.length);
  }

  void setPrincipal(@NotNull TBItem item) { NST.setPrincipal(myNativePeer, item.myUid); }

  void onBeforeShow() {
    if (myItems.hasAnActionItems()) {
      updateActionItems();
      if (myTimerListener == null) {
        myTimerListener = new TimerListener() {
          @Override
          public ModalityState getModalityState() { return ModalityState.current(); }
          @Override
          public void run() { updateActionItems(); }
        };
      }
      ActionManager.getInstance().addTransparentTimerListener(500/*delay param doesn't affect anything*/, myTimerListener);
    }
  }
  void onHide() { _stopTimer(); }
  void onClose() {
    _stopTimer();
    if (myReleaseOnClose)
      release();
  }

  void forEachDeep(Consumer<? super TBItem> proc) { myItems.forEachDeep(proc); }

  void updateActionItems() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final boolean[] layoutChanged = new boolean[]{false};
    forEachDeep(tbitem->{
      if (!(tbitem instanceof TBItemAnActionButton))
        return;

      final TBItemAnActionButton item = (TBItemAnActionButton)tbitem;
      final Presentation presentation = myPresentationFactory.getPresentation(item.getAnAction());

      try {
        item.updateAnAction(presentation);
      } catch (IndexNotReadyException e1) {
        presentation.setEnabled(false);
        presentation.setVisible(false);
      }

      if (item.isAutoVisibility()) {
        final boolean itemVisibilityChanged = item.updateVisibility(presentation);
        if (itemVisibilityChanged)
          layoutChanged[0] = true;
      }
      item.updateView(presentation);
    });

    if (layoutChanged[0])
      selectVisibleItemsToShow();
  }

  void setComponent(Component component/*for DataContext*/) {
    myItems.forEachDeep(item -> {
      if (item instanceof TBItemAnActionButton)
        ((TBItemAnActionButton)item).setComponent(component);
    });
  }

  private void _closeSelf() { TouchBarsManager.closeTouchBar(this); }

  private void _stopTimer() {
    if (myTimerListener != null) {
      ActionManager.getInstance().removeTransparentTimerListener(myTimerListener);
      myTimerListener = null;
    }
  }
}

class SpacingItem extends TBItem {
  SpacingItem(@NotNull String uid) { super(uid, null); }
  @Override
  protected void _updateNativePeer() {} // mustn't be called
  @Override
  protected ID _createNativePeer() { return null; } // mustn't be called
}