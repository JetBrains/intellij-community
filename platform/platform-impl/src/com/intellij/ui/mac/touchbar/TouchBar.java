// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.mac.TouchbarDataKeys;
import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

class TouchBar implements NSTLibrary.ItemCreator {
  private static final Logger LOG = Logger.getInstance(TouchBar.class);

  private final ItemsContainer myItems;
  private final ItemListener myItemListener;
  private final TBItemButton myCustomEsc;
  private final @NotNull UpdateTimerWrapper myUpdateTimer = new UpdateTimerWrapper(500);
  private long myStartShowNs = 0;

  private ID myNativePeer;        // java wrapper holds native object
  private String myDefaultOptionalContextName;
  private BarContainer myBarContainer;

  public static final TouchBar EMPTY = new TouchBar();

  private TouchBar() {
    myItems = new ItemsContainer("EMPTY_STUB_TOUCHBAR", null);
    myCustomEsc = null;
    myNativePeer = ID.NIL;
    myItemListener = null;
  }

  TouchBar(@NotNull String touchbarName, boolean replaceEsc) {
    this(touchbarName, replaceEsc, false, false);
  }

  TouchBar(@NotNull String touchbarName, boolean replaceEsc, boolean autoClose, boolean emulateESC) {
    if (autoClose) {
      myItemListener = (src, evcode) -> {
        // NOTE: called from AppKit thread
        _closeSelf();
      };
    } else
      myItemListener = null;

    myItems = new ItemsContainer(touchbarName, myItemListener);
    if (replaceEsc) {
      final Icon ic = IconLoader.getIcon("/mac/touchbar/popoverClose_dark.svg");
      myCustomEsc = new TBItemButton(touchbarName + "_custom_esc_button", myItemListener).setIcon(ic).setWidth(64).setTransparentBg(true).setThreadSafeAction(()-> {
        _closeSelf();
        if (emulateESC) {
          try {
            // https://stackoverflow.com/questions/10468432/do-robot-methods-need-to-be-run-on-the-event-queue
            // The Robot methods you mentioned should not be run on the EDT.
            // If you call any of these methods on the EDT while Robot.isAutoWaitForIdle is true, an exception will be thrown.
            // This stands to reason that even if isAutoWaitForIdle is false, these methods shouldn't be called from the EDT.
            Robot robot = new Robot();
            robot.keyPress(KeyEvent.VK_ESCAPE);
            robot.keyRelease(KeyEvent.VK_ESCAPE);
          } catch (AWTException e) {
            LOG.error(e);
          }
        }
      });
    } else
      myCustomEsc = null;

    myNativePeer = NST.createTouchBar(touchbarName, this, myCustomEsc != null ? myCustomEsc.myUid : null);
  }

  static TouchBar buildFromCustomizedGroup(@NotNull String touchbarName, @NotNull ActionGroup customizedGroup, boolean replaceEsc) {
    final TouchBar result = new TouchBar(touchbarName, replaceEsc);

    final String groupId = BuildUtils.getActionId(customizedGroup);
    if (groupId == null) {
      LOG.error("unregistered customized group: " + customizedGroup);
      return result;
    }

    final String filterPrefix = groupId + "_";
    result.myDefaultOptionalContextName = groupId + "OptionalGroup";
    final BuildUtils.Customizer customizer = new BuildUtils.Customizer() {
      @Override
      public void process(@NotNull BuildUtils.INodeInfo ni, @NotNull TBItemAnActionButton butt) {
        super.process(ni, butt);
        if (result.myDefaultOptionalContextName.equals(ni.getParentGroupID()))
          butt.myOptionalContextName = result.myDefaultOptionalContextName;
      }
    };
    BuildUtils.addActionGroupButtons(result, customizedGroup, filterPrefix, customizer);
    result.selectVisibleItemsToShow();
    return result;
  }

  static TouchBar buildFromGroup(@NotNull String touchbarName, @NotNull ActionGroup actions, boolean replaceEsc, boolean emulateESC) {
    final TouchbarDataKeys.ActionDesc groupDesc = actions.getTemplatePresentation().getClientProperty(TouchbarDataKeys.ACTIONS_DESCRIPTOR_KEY);
    if (groupDesc != null && !groupDesc.isReplaceEsc())
      replaceEsc = false;
    final TouchBar result = new TouchBar(touchbarName, replaceEsc, false, emulateESC);
    addActionGroup(result, actions);
    return result;
  }

  static void addActionGroup(TouchBar result, @NotNull ActionGroup actions) {
    final @Nullable ModalityState ms = Utils.getCurrentModalityState();
    final @Nullable TouchbarDataKeys.ActionDesc groupDesc = actions.getTemplatePresentation().getClientProperty(TouchbarDataKeys.ACTIONS_DESCRIPTOR_KEY);
    final BuildUtils.Customizer customizer = new BuildUtils.Customizer(groupDesc, ms);
    addActionGroup(result, actions, customizer);
  }

  static void addActionGroup(TouchBar result, @NotNull ActionGroup actions, @NotNull BuildUtils.Customizer customizer) {
    BuildUtils.addActionGroupButtons(result, actions, null, customizer);
    result.selectVisibleItemsToShow();
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
      LOG.error("can't find TBItem with uid '" + uid + "'");
      return ID.NIL;
    }
    // System.out.println("create native peer for item '" + uid + "'");
    return item.getNativePeer();
  }

  ID getNativePeer() { return myNativePeer; }
  ItemsContainer getItemsContainer() { return myItems; }

  void release() {
    myItems.releaseAll();
    if (!myNativePeer.equals(ID.NIL)) {
      NST.releaseTouchBar(myNativePeer);
      myNativePeer = ID.NIL;
    }
    myUpdateTimer.stop();
  }

  void clear() { myItems.releaseAll(); }

  //
  // NOTE: must call 'selectVisibleItemsToShow' after touchbar filling
  //
  @NotNull TBItemButton addButton() { return myItems.addButton(); }
  @NotNull TBItemAnActionButton addAnActionButton(@NotNull AnAction act) { return myItems.addAnActionButton(act); }
  @NotNull TBItemAnActionButton addAnActionButton(@NotNull AnAction act, @Nullable TBItem positionAnchor) { return myItems.addAnActionButton(act, positionAnchor); }
  @NotNull TBItemGroup addGroup() { return myItems.addGroup(); }
  @NotNull TBItemScrubber addScrubber() { return myItems.addScrubber(); }
  @NotNull TBItemPopover addPopover(Icon icon, String text, int width, TouchBar expandTB, TouchBar tapAndHoldTB) {
    return myItems.addPopover(icon, text, width, expandTB, tapAndHoldTB);
  }
  @NotNull void addSpacing(boolean large) { myItems.addSpacing(large); }
  @NotNull void addFlexibleSpacing() { myItems.addFlexibleSpacing(); }

  void setBarContainer(BarContainer barContainer) { myBarContainer = barContainer; }

  void setDefaultOptionalContextName(@NotNull String defaultCtxName) { myDefaultOptionalContextName = defaultCtxName; }

  void setOptionalContextItems(@NotNull ActionGroup actions, @NotNull String contextName) {
    myItems.releaseItems(tbi -> contextName.equals(tbi.myOptionalContextName));
    BuildUtils.addActionGroupButtons(
      this, actions, null,
      new BuildUtils.Customizer() {
        @Override
        public void process(@NotNull BuildUtils.INodeInfo ni, @NotNull TBItemAnActionButton butt) {
          super.process(ni, butt);
          butt.myOptionalContextName = contextName;
        }
    });
    selectVisibleItemsToShow();
  }

  void removeOptionalContextItems(@NotNull String contextName) {
    myItems.releaseItems(tbi -> contextName.equals(tbi.myOptionalContextName));
    selectVisibleItemsToShow();
  }

  // when contextName == null sets default optional-context
  void setOptionalContextVisible(@Nullable String contextName) {
    final @Nullable String ctx = contextName == null ? myDefaultOptionalContextName : contextName;

    final boolean visibilityChanged[] = {false};
    myItems.forEachDeep(tbi -> {
      if (tbi.myOptionalContextName == null)
        return;

      final boolean newVisible = tbi.myOptionalContextName.equals(ctx);
      if (tbi.myIsVisible != newVisible) {
        if (tbi instanceof TBItemAnActionButton)
          ((TBItemAnActionButton)tbi).setAutoVisibility(newVisible);
        tbi.myIsVisible = newVisible;
        visibilityChanged[0] = true;
      }
    });

    if (visibilityChanged[0])
      selectVisibleItemsToShow();
  }

  void selectVisibleItemsToShow() {
    if (myItems.isEmpty())
      return;

    final String[] ids = myItems.getVisibleIds();
    NST.selectItemsToShow(myNativePeer, ids, ids.length);
  }

  void setPrincipal(@NotNull TBItem item) { NST.setPrincipal(myNativePeer, item.myUid); }

  void onBeforeShow() {
    myStartShowNs = System.nanoTime();
    updateActionItems();
    myUpdateTimer.start();
  }
  void onHide() {
    myUpdateTimer.stop();
  }

  void forEachDeep(Consumer<? super TBItem> proc) { myItems.forEachDeep(proc); }

  void updateActionItems() {
    // When user types text and presses modifier keys it causes to show alternative touchbar layouts, some of them are visible less than second.
    // To avoid unnecessary slow-update invocations for such bars we always try to use cached presentations for the first 500 ms (for slow actions only)
    final long elapsedFromStartShowNs = System.nanoTime() - myStartShowNs;
    final boolean forceUseCached = elapsedFromStartShowNs < 500*1000000l;

    final boolean[] layoutChanged = new boolean[]{false};
    forEachDeep(tbitem->{
      if (!(tbitem instanceof TBItemAnActionButton))
        return;

      final TBItemAnActionButton item = (TBItemAnActionButton)tbitem;
      final @NotNull Presentation presentation = item.updateAnAction(forceUseCached);

      final boolean itemVisibilityChanged = item.updateVisibility(presentation);
      if (itemVisibilityChanged)
        layoutChanged[0] = true;
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

  private void _closeSelf() {
    if (myBarContainer == null) {
      LOG.error("can't perform _closeSelf for touchbar '" + toString() + "' because parent container wasn't set");
      return;
    }
    TouchBarsManager.hideContainer(myBarContainer);
  }

  private class UpdateTimerWrapper {
    final int myDelay;
    Object myTimerImpl;

    UpdateTimerWrapper(int delay) { myDelay = delay; }

    void start() {
      if (myTimerImpl != null)
        stop();

      if (ApplicationManager.getApplication() == null) {
        final Timer t = new Timer(myDelay, (event) -> updateActionItems());
        myTimerImpl = t;

        t.setRepeats(true);
        t.start();
        return;
      }

      final TimerListener t = new TimerListener() {
        @Override
        public ModalityState getModalityState() { return ModalityState.current(); }
        @Override
        public void run() { updateActionItems(); }
      };
      myTimerImpl = t;
      ActionManager.getInstance().addTransparentTimerListener(myDelay/*delay param doesn't affect anything*/, t);
    }

    void stop() {
      if (myTimerImpl == null)
        return;

      if (myTimerImpl instanceof Timer)
        ((Timer)myTimerImpl).stop();
      else if (myTimerImpl instanceof TimerListener)
        ActionManager.getInstance().removeTransparentTimerListener((TimerListener)myTimerImpl);

      myTimerImpl = null;
    }
  }
}

class SpacingItem extends TBItem {
  SpacingItem(@NotNull String uid) { super(uid, null); }
  @Override
  protected void _updateNativePeer() {} // mustn't be called
  @Override
  protected ID _createNativePeer() { return ID.NIL; } // mustn't be called
}