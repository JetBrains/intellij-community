// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.CancellablePromise;

import javax.swing.Timer;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

final class TouchBar implements NSTLibrary.ItemCreator {
  private static final boolean ourUseCached = Registry.is("actionSystem.update.touchbar.actions.use.cached");
  private static final boolean ourCollectStats = Boolean.getBoolean("touchbar.collect.stats");
  private static final Logger LOG = Logger.getInstance(TouchBar.class);
  private final @Nullable TouchBarStats myStats;

  private final @NotNull ItemsContainer myItems;
  private final ItemListener myItemListener;
  private final PresentationFactory myFactory = new PresentationFactory();
  private final TBItemButton myCustomEsc;
  private final ActionGroup myActionGroup;
  private final @Nullable String mySkipSubgroupsPrefix;
  private final @NotNull Updater myUpdateTimer = new Updater();
  private CancellablePromise<List<AnAction>> myLastUpdate;
  private String[] myVisibleIds;
  private long myStartShowNs = 0;
  private long myLastUpdateNs = 0;

  private Timer myAutoCloseTimer = null;
  private long myLastActiveNs = 0;

  private ID myNativePeer;        // java wrapper holds native object
  private String myDefaultOptionalContextName;
  private BarContainer myBarContainer;

  private final Object myHideReleaseLock = new Object();
  private Future<?> myLastUpdateNativePeers;

  private boolean myAllowSkipSlowUpdates = false;

  private final @NotNull Map<AnAction, TBItemAnActionButton> myActionButtonPool = new HashMap<>();
  private final @NotNull LinkedList<TBItemGroup> myGroupPool = new LinkedList<>();

  public static final TouchBar EMPTY = new TouchBar();

  private TouchBar() {
    myItems = new ItemsContainer("EMPTY_STUB_TOUCHBAR");
    myCustomEsc = null;
    myNativePeer = ID.NIL;
    myItemListener = null;
    myActionGroup = null;
    mySkipSubgroupsPrefix = null;
    myStats = null;
  }

  TouchBar(@NotNull String touchbarName, boolean replaceEsc) {
    this(touchbarName, replaceEsc, false, false, null, null);
  }

  TouchBar(@NotNull String touchbarName,
           boolean replaceEsc,
           boolean autoClose,
           boolean emulateESC,
           @Nullable ActionGroup actionGroup,
           @Nullable String skipSubgroupsPrefix) {
    if (autoClose) {
      myItemListener = (src, evcode) -> {
        // NOTE: called from AppKit thread
        _closeSelf();
      };
    }
    else {
      myItemListener = null;
    }

    myActionGroup = actionGroup;
    mySkipSubgroupsPrefix = skipSubgroupsPrefix;
    myStats = ourCollectStats ? TouchBarStats.getStats(touchbarName) : null;
    myItems = new ItemsContainer(touchbarName);
    if (replaceEsc) {
      final Icon ic = AllIcons.Mac.Touchbar.PopoverClose;
      myCustomEsc = new TBItemButton(myItemListener, null).setIcon(ic).setWidth(64).setTransparentBg(true).setAction(() -> {
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
          }
          catch (AWTException e) {
            LOG.error(e);
          }
        }
      }, false, null);
      myCustomEsc.setUid(touchbarName + "_custom_esc_button");
    }
    else {
      myCustomEsc = null;
    }

    myNativePeer = NST.createTouchBar(touchbarName, this, myCustomEsc != null ? myCustomEsc.getUid() : null);
  }

  @NotNull
  PresentationFactory getFactory() {
    return myFactory;
  }

  void setAllowSkipSlowUpdates(boolean allowSkipSlowUpdates) {
    myAllowSkipSlowUpdates = allowSkipSlowUpdates;
  }

  @Nullable
  TouchBarStats getStats() {
    return myStats;
  }

  boolean isManualClose() {
    return myCustomEsc != null;
  }

  boolean isEmpty() {
    return isEmptyActionGroup() && (myItems.isEmpty() || !myItems.anyMatchDeep(item -> item != null && !(item instanceof SpacingItem)));
  }

  @Override
  public String toString() {
    return myItems + "_" + myNativePeer;
  }

  @Override
  public ID createItem(@NotNull String uid) { // called from AppKit (when NSTouchBarDelegate create items)
    final long startNs = myStats != null ? System.nanoTime() : 0;
    final ID result = createItemImpl(uid);
    if (myStats != null) {
      myStats.incrementCounter(StatsCounters.itemsCreationDurationNs, System.nanoTime() - startNs);
    }
    return result;
  }

  private ID createItemImpl(@NotNull String uid) {
    if (myCustomEsc != null && myCustomEsc.getUid().equals(uid)) {
      return myCustomEsc.getNativePeer();
    }

    TBItem item = myItems.findItem(uid);
    if (item == null) {
      LOG.error("can't find TBItem with uid '" + uid + "'");
      return ID.NIL;
    }

    return item.getNativePeer();
  }

  ID getNativePeer() {
    return myNativePeer;
  }

  @NotNull
  ItemsContainer getItemsContainer() {
    return myItems;
  }

  void release() {
    synchronized (myHideReleaseLock) {
      final long startNs = myStats != null ? System.nanoTime() : 0;
      myItems.releaseAll();
      if (!myNativePeer.equals(ID.NIL)) {
        NST.releaseTouchBar(myNativePeer);
        myNativePeer = ID.NIL;
      }
      myUpdateTimer.stop();

      myActionButtonPool.forEach((act, item) -> item.releaseNativePeer());
      myActionButtonPool.clear();
      myGroupPool.forEach(item -> item.releaseNativePeer());
      myGroupPool.clear();
      if (myStats != null) {
        myStats.incrementCounter(StatsCounters.touchbarReleaseDurationNs, System.nanoTime() - startNs);
      }
    }
  }

  void softClear() {
    myItems.softClear(myActionButtonPool, myGroupPool);
  }

  //
  // NOTE: must call 'selectVisibleItemsToShow' after touchbar filling
  //
  @NotNull
  TBItemButton addButton() {
    @NotNull TBItemButton butt = new TBItemButton(myItemListener, myStats != null ? myStats.getActionStats("simple_button") : null);
    myItems.addItem(butt);
    return butt;
  }

  @NotNull
  TBItemAnActionButton addAnActionButton(@NotNull AnAction act) { return addAnActionButton(act, null); }

  @NotNull
  TBItemAnActionButton addAnActionButton(@NotNull AnAction act, @Nullable TBItem positionAnchor) {
    @NotNull TBItemAnActionButton butt = createActionButton(act);
    myItems.addItem(butt, positionAnchor);
    return butt;
  }

  @NotNull
  TBItemGroup addGroup() {
    @NotNull TBItemGroup group = createGroup();
    myItems.addItem(group);
    return group;
  }

  @NotNull
  TBItemScrubber addScrubber() {
    final int defaultScrubberWidth = 500;
    @NotNull TBItemScrubber scrub = new TBItemScrubber(myItemListener, myStats, defaultScrubberWidth);
    myItems.addItem(scrub);
    return scrub;
  }

  void addSpacing(boolean large) { myItems.addSpacing(large); }

  void addFlexibleSpacing() { myItems.addFlexibleSpacing(); }

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

    final boolean[] visibilityChanged = {false};
    myItems.forEachDeep(tbi -> {
      if (tbi.myOptionalContextName == null) {
        return;
      }

      final boolean newVisible = tbi.myOptionalContextName.equals(ctx);
      if (tbi.myIsVisible != newVisible) {
        if (tbi instanceof TBItemAnActionButton) {
          ((TBItemAnActionButton)tbi).setAutoVisibility(newVisible);
        }
        tbi.myIsVisible = newVisible;
        visibilityChanged[0] = true;
      }
    });

    if (visibilityChanged[0]) {
      selectVisibleItemsToShow();
    }
  }

  void selectVisibleItemsToShow() {
    if (myItems.isEmpty()) {
      if (myVisibleIds != null && myVisibleIds.length > 0) {
        NST.selectItemsToShow(myNativePeer, null, 0);
      }
      myVisibleIds = null;
      return;
    }

    String[] ids = myItems.getVisibleIds();
    if (Arrays.equals(ids, myVisibleIds)) {
      return;
    }

    myVisibleIds = ids;
    NST.selectItemsToShow(myNativePeer, ids, ids.length);
  }

  void setPrincipal(@NotNull TBItem item) {
    NST.setPrincipal(myNativePeer, item.getUid());
  }

  void onBeforeShow() {
    myStartShowNs = System.nanoTime();
    myUpdateTimer.start();
    myAutoCloseTimer = null;
    myLastActiveNs = 0;
    updateActionItems();
  }

  void onHide() {
    synchronized (myHideReleaseLock) {
      if (myLastUpdate != null) {
        myLastUpdate.cancel();
        myLastUpdate = null;
      }
      myUpdateTimer.stop();
    }
  }

  void forEachDeep(Consumer<? super TBItem> proc) { myItems.forEachDeep(proc); }

  private void _applyPresentationChanges(List<AnAction> actions) {
    final long startNs = System.nanoTime();
    if (myLastUpdateNativePeers != null && !myLastUpdateNativePeers.isDone()) {
      myLastUpdateNativePeers.cancel(false);
    }

    final List<TBItemButton.Updater> toUpdate = new ArrayList<>();
    final boolean[] checks = myBarContainer != null && myBarContainer.getType() == BarType.DEBUGGER ? new boolean[]{false, false} : null;

    forEachDeep(tbitem -> {
      if (!(tbitem instanceof TBItemAnActionButton)) {
        return;
      }

      final TBItemAnActionButton item = (TBItemAnActionButton)tbitem;
      final @NotNull Presentation presentation = myFactory.getPresentation(item.getAnAction());
      item.updateVisibility(presentation);
      item.updateView(presentation);
      final @Nullable TBItemButton.Updater updater = item.getNativePeerUpdater();
      if (updater != null) {
        toUpdate.add(updater);
      }

      // temporary solution to avoid IDEA-227511 MacBook touch bar stuck after debugging
      // will be removed after global refactoring
      if (checks != null) {
        final String actId = ActionManager.getInstance().getId(item.getAnAction());
        if ("Resume".equals(actId))
          checks[0] = !presentation.isEnabled();
        else if ("Pause".equals(actId))
          checks[1] = !presentation.isEnabled();
      }
    });

    final Runnable updateAllNativePeers = () -> {
      toUpdate.forEach(item -> item.prepareUpdateData());

      synchronized (myHideReleaseLock) {
        if (!myUpdateTimer.isRunning() || myNativePeer.equals(ID.NIL)) {
          return; // was hidden or released
        }

        toUpdate.forEach(item -> item.updateNativePeer());
      }
    };
    final @NotNull Application app = ApplicationManager.getApplication();
    myLastUpdateNativePeers = app.executeOnPooledThread(() -> app.runReadAction(updateAllNativePeers));

    selectVisibleItemsToShow();
    if (myStats != null) {
      myStats.incrementCounter(StatsCounters.applyPresentaionChangesDurationNs, System.nanoTime() - startNs);
    }

    if (checks != null) {
      final boolean isNonActive = checks[0] && checks[1];
      if (!isNonActive)
        myLastActiveNs = startNs;
      if (isNonActive && myLastActiveNs > 0) {
        if (myAutoCloseTimer == null) {
          myAutoCloseTimer = new Timer(1500, __ -> _closeSelf());
          myAutoCloseTimer.setRepeats(false);
          myAutoCloseTimer.start();
        }
      } else {
        if (myAutoCloseTimer != null) {
          myAutoCloseTimer.stop();
          myAutoCloseTimer = null;
        }
      }
    }
  }

  void updateActionItems() {
    if (!myUpdateTimer.isRunning()) // don't update actions if was hidden
    {
      return;
    }

    final long timeNs = System.nanoTime();
    final long elapsedFromStartShowNs = timeNs - myStartShowNs;
    myLastUpdateNs = timeNs;

    int delayMillis = 500;
    long delayNanos = TimeUnit.MILLISECONDS.toNanos(delayMillis);
    if (ourUseCached && elapsedFromStartShowNs < delayNanos) {
      // When user types text and presses modifier keys it causes to show alternative touchbar layouts, some of them are visible less than second.
      // To avoid unnecessary slow-update invocations for such bars we always try to use cached presentations for the first 500 ms
      if (myStats != null) {
        myStats.incrementCounter(StatsCounters.forceUseCached);
      }
      // start manual timer to update action-buttons if necessary
      final Timer t = new Timer(delayMillis, e -> {
        if (System.nanoTime() - myLastUpdateNs > delayNanos) { // update action-buttons if last update was long time ago
          if (myStats != null) {
            myStats.incrementCounter(StatsCounters.forceCachedDelayedUpdateCount);
          }
          updateActionItems();
        }
      });
      t.setRepeats(false);
      t.start();
      return;
    }

    if (myActionGroup != null) {
      DataContext dataContext = Utils.wrapDataContext(DataManager.getInstance().getDataContext(BuildUtils.getCurrentFocusComponent()));
      BuildUtils.GroupVisitor visitor = new BuildUtils.GroupVisitor(this, mySkipSubgroupsPrefix, null, myStats, myAllowSkipSlowUpdates);
      if (Utils.isAsyncDataContext(dataContext)) {
        if (myLastUpdate != null) myLastUpdate.cancel();
        myLastUpdate = Utils.expandActionGroupAsync(LaterInvocator.isInModalContext(), myActionGroup, myFactory,
                                                    dataContext, ActionPlaces.TOUCHBAR_GENERAL, visitor);
        myLastUpdate.onSuccess(actions -> _applyPresentationChanges(actions)).onProcessed(__ -> myLastUpdate = null);
      }
      else {
        List<AnAction> actions = Utils.expandActionGroupWithTimeout(
          LaterInvocator.isInModalContext(),
          myActionGroup,
          myFactory, dataContext,
          ActionPlaces.TOUCHBAR_GENERAL,
          visitor, Registry.intValue("actionSystem.update.touchbar.timeout.ms"));
        _applyPresentationChanges(actions);
      }
    }
    else {
      forEachDeep(tbitem -> {
        if (!(tbitem instanceof TBItemAnActionButton)) {
          return;
        }

        final long startNs = myStats != null ? System.nanoTime() : 0;
        final @NotNull TBItemAnActionButton item = (TBItemAnActionButton)tbitem;
        final @NotNull Presentation presentation = myFactory.getPresentation(item.getAnAction());

        final Component component = item.getComponent();

        final DataContext dctx = DataManager.getInstance().getDataContext(component);
        final ActionManager am = ActionManagerEx.getInstanceEx();
        final AnActionEvent e = new AnActionEvent(
          null,
          dctx,
          ActionPlaces.TOUCHBAR_GENERAL,
          presentation,
          am,
          0
        );

        try {
          ActionUtil.performFastUpdate(false, item.getAnAction(), e, false);
        }
        catch (IndexNotReadyException e1) {
          presentation.setEnabledAndVisible(false);
        }

        if (myStats != null) {
          myStats.getActionStats(item.getActionId()).onUpdate(System.nanoTime() - startNs);
        }
      });

      _applyPresentationChanges(null);
    }

    if (myStats != null) {
      myStats.incrementCounter(StatsCounters.totalUpdateDurationNs, System.nanoTime() - timeNs);
    }
  }

  private void _closeSelf() {
    if (myBarContainer == null) {
      LOG.error("can't perform _closeSelf for touchbar '" + this + "' because parent container wasn't set");
      return;
    }
    TouchBarsManager.hideContainer(myBarContainer);
  }

  private final class Updater {
    private @Nullable TimerListener myTimerImpl;

    void start() {
      if (myTimerImpl != null) {
        stop();
      }

      myTimerImpl = new TimerListener() {
        @Override
        public ModalityState getModalityState() {
          return ModalityState.current();
        }

        @Override
        public void run() {
          updateActionItems();
        }
      };
      ActionManager.getInstance().addTimerListener(-1, myTimerImpl);
    }

    void stop() {
      if (myTimerImpl == null) {
        return;
      }

      ActionManager.getInstance().removeTimerListener(myTimerImpl);
      myTimerImpl = null;
    }

    boolean isRunning() {
      return myTimerImpl != null;
    }
  }

  @NotNull TBItemAnActionButton createActionButton(@NotNull AnAction acaction) {
    TouchBarStats.AnActionStats stats;
    if (myStats == null) {
      stats = null;
    }
    else {
      stats = myStats.getActionStats(BuildUtils.getActionId(acaction));
    }
    TBItemAnActionButton cached = myActionButtonPool.remove(acaction);
    return cached != null ? cached : new TBItemAnActionButton(myItemListener, acaction, stats);
  }

  @NotNull TBItemGroup createGroup() {
    return myGroupPool.isEmpty() ? new TBItemGroup(myItems.toString(), myItemListener) : myGroupPool.poll();
  }

  private boolean isEmptyActionGroup() {
    if (myActionGroup == null) {
      return true;
    }

    AnAction[] actions = myActionGroup.getChildren(null);
    if (actions.length == 0) {
      return true;
    }

    for (AnAction act : actions) {
      if (!(act instanceof Separator)) {
        return false;
      }
    }
    return true;
  }
}

final class SpacingItem extends TBItem {
  SpacingItem() { super("space", null); }

  @Override
  protected ID _createNativePeer() { return ID.NIL; } // mustn't be called
}