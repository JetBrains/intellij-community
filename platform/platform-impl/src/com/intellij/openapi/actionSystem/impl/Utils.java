// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ProhibitAWTEvents;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.keymap.impl.ActionProcessor;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.SlowOperations;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.concurrency.EdtScheduledExecutorService;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.CancellablePromise;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

public final class Utils {
  private static final Logger LOG = Logger.getInstance(Utils.class);

  public static final AnAction EMPTY_MENU_FILLER = new EmptyAction();
  static {
    EMPTY_MENU_FILLER.getTemplatePresentation().setText(CommonBundle.messagePointer("empty.menu.filler"));
  }

  public static @NotNull DataContext wrapToAsyncDataContext(@NotNull DataContext dataContext) {
    Component component = dataContext.getData(PlatformDataKeys.CONTEXT_COMPONENT);
    if (dataContext instanceof DataManagerImpl.MyDataContext) {
      return new PreCachedDataContext(component);
    }
    else if (dataContext instanceof SimpleDataContext && component != null) {
      PreCachedDataContext wrapped = new PreCachedDataContext(component);
      LOG.assertTrue(wrapped.getData(CommonDataKeys.PROJECT) == dataContext.getData(CommonDataKeys.PROJECT));
      LOG.warn(new Throwable("Use DataManager.getDataContext(component) instead of SimpleDataContext for wrapping."));
      return wrapped;
    }
    return dataContext;
  }

  public static @NotNull DataContext wrapDataContext(@NotNull DataContext dataContext) {
    if (!Registry.is("actionSystem.update.actions.async")) return dataContext;
    return wrapToAsyncDataContext(dataContext);
  }

  @ApiStatus.Internal
  public static @NotNull DataContext freezeDataContext(@NotNull DataContext dataContext, @Nullable Consumer<? super String> missedKeys) {
    return dataContext instanceof PreCachedDataContext ? ((PreCachedDataContext)dataContext).frozenCopy(missedKeys) : dataContext;
  }

  public static boolean isAsyncDataContext(@NotNull DataContext dataContext) {
    return dataContext instanceof PreCachedDataContext;
  }

  @ApiStatus.Internal
  public static @Nullable Object getRawDataIfCached(@NotNull DataContext dataContext, @NotNull String dataId) {
    return dataContext instanceof PreCachedDataContext ? ((PreCachedDataContext)dataContext).getRawDataIfCached(dataId) :
           dataContext instanceof EdtDataContext ? ((EdtDataContext)dataContext).getRawDataIfCached(dataId) : null;
  }

  @ApiStatus.Internal
  public static CancellablePromise<List<AnAction>> expandActionGroupAsync(boolean isInModalContext,
                                                                          @NotNull ActionGroup group,
                                                                          @NotNull PresentationFactory presentationFactory,
                                                                          @NotNull DataContext context,
                                                                          @NotNull String place) {
    return new ActionUpdater(isInModalContext, presentationFactory, context, place, false, false)
      .expandActionGroupAsync(group, group instanceof CompactActionGroup);
  }

  @ApiStatus.Internal
  public static List<AnAction> expandActionGroupWithTimeout(boolean isInModalContext,
                                                            @NotNull ActionGroup group,
                                                            @NotNull PresentationFactory presentationFactory,
                                                            @NotNull DataContext context,
                                                            @NotNull String place,
                                                            int timeoutMs) {
    return new ActionUpdater(isInModalContext, presentationFactory, context, place, false, false)
      .expandActionGroupWithTimeout(group, group instanceof CompactActionGroup, timeoutMs);
  }

  private static final boolean DO_FULL_EXPAND = Boolean.getBoolean("actionSystem.use.full.group.expand"); // for tests and debug

  public static @NotNull List<AnAction> expandActionGroup(boolean isInModalContext,
                                                          @NotNull ActionGroup group,
                                                          @NotNull PresentationFactory presentationFactory,
                                                          @NotNull DataContext context,
                                                          @NotNull String place) {
    return expandActionGroupImpl(isInModalContext, group, presentationFactory, context,
                                 place, ActionPlaces.isPopupPlace(place), null);

  }

  private static @NotNull List<AnAction> expandActionGroupImpl(boolean isInModalContext,
                                                               @NotNull ActionGroup group,
                                                               @NotNull PresentationFactory presentationFactory,
                                                               @NotNull DataContext context,
                                                               @NotNull String place,
                                                               boolean isContextMenu,
                                                               @Nullable Runnable onProcessed) {
    boolean async = isAsyncDataContext(context);
    boolean asyncUI = async && Registry.is("actionSystem.update.actions.async.ui");
    BlockingQueue<Runnable> queue0 = async && !asyncUI ? new LinkedBlockingQueue<>() : null;
    ActionUpdater updater = new ActionUpdater(
      isInModalContext, presentationFactory, context, place, isContextMenu, false, null, queue0 != null ? queue0::offer : null);
    List<AnAction> list;
    if (async) {
      Set<String> missedKeys = new HashSet<>();
      list = expandActionGroupFastTrack(updater, group, group instanceof CompactActionGroup, missedKeys::add);
      if (list != null && missedKeys.isEmpty()) {
        if (onProcessed != null) onProcessed.run();
        return list;
      }
      IdeEventQueue queue = IdeEventQueue.getInstance();
      CancellablePromise<List<AnAction>> promise = updater.expandActionGroupAsync(group, group instanceof CompactActionGroup);
      if (onProcessed != null) promise.onProcessed(__ -> onProcessed.run());
      try (AccessToken ignore = cancelOnUserActivityInside(promise)) {
        list = runLoopAndWaitForFuture(promise, Collections.emptyList(), () -> {
          if (queue0 != null) {
            Runnable runnable = queue0.poll(1, TimeUnit.MILLISECONDS);
            if (runnable != null) runnable.run();
          }
          else {
            AWTEvent event = queue.getNextEvent();
            queue.dispatchEvent(event);
          }
        });
      }
      if (promise.isCancelled()) {
        // to avoid duplicate "Nothing Here" items in menu bar
        // and "Nothing Here"-only popup menus
        throw new ProcessCanceledException();
      }
    }
    else {
      if (Registry.is("actionSystem.update.actions.async") && !ApplicationManager.getApplication().isUnitTestMode()) {
        LOG.warn(new Throwable("Non-async data context detected in async mode in '" + place + "': " + context.getClass().getName()));
      }
      try {
        list = DO_FULL_EXPAND ?
               updater.expandActionGroupFull(group, group instanceof CompactActionGroup) :
               updater.expandActionGroupWithTimeout(group, group instanceof CompactActionGroup);
      }
      finally {
        if (onProcessed != null) onProcessed.run();
      }
    }
    return list;
  }

  private static @NotNull AccessToken cancelOnUserActivityInside(@NotNull CancellablePromise<List<AnAction>> promise) {
    Component focusOwner = IdeFocusManager.getGlobalInstance().getFocusOwner();
    return ProhibitAWTEvents.startFiltered("expandActionGroup", event -> {
      if (event instanceof FocusEvent && !((FocusEvent)event).isTemporary() && event.getID() == FocusEvent.FOCUS_GAINED &&
          focusOwner != null && !UIUtil.isAncestor(focusOwner, ((FocusEvent)event).getComponent()) ||
          event instanceof KeyEvent && event.getID() == KeyEvent.KEY_PRESSED ||
          event instanceof MouseEvent && event.getID() == MouseEvent.MOUSE_PRESSED) {
        promise.cancel();
      }
      return null;
    });
  }

  static @Nullable List<AnAction> expandActionGroupFastTrack(@NotNull ActionUpdater updater,
                                                             @NotNull ActionGroup group,
                                                             boolean hideDisabled,
                                                             @Nullable Consumer<String> missedKeys) {
    int maxTime = Registry.intValue("actionSystem.update.actions.async.fast-track.timeout.ms", 20);
    if (maxTime < 1) return null;
    BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    ActionUpdater fastUpdater = ActionUpdater.getActionUpdater(updater.asFastUpdateSession(missedKeys, queue::offer));
    try (AccessToken ignore = SlowOperations.allowSlowOperations(SlowOperations.FAST_TRACK)) {
      long start = System.currentTimeMillis();
      ActionUpdater.cancelAllUpdates();
      CancellablePromise<List<AnAction>> promise = fastUpdater.expandActionGroupAsync(group, hideDisabled);
      return runLoopAndWaitForFuture(promise, null, () -> {
        Runnable runnable = queue.poll(1, TimeUnit.MILLISECONDS);
        if (runnable != null) runnable.run();
        long elapsed = System.currentTimeMillis() - start;
        if (elapsed > maxTime) {
          promise.cancel();
        }
      });
    }
  }

  static void fillMenu(@NotNull ActionGroup group,
                       @NotNull JComponent component,
                       boolean enableMnemonics,
                       @NotNull PresentationFactory presentationFactory,
                       @NotNull DataContext context,
                       @NotNull String place,
                       boolean isWindowMenu,
                       boolean useDarkIcons,
                       @Nullable RelativePoint relativePoint) {
    Runnable removeIcon = addLoadingIcon(relativePoint, context, place);
    List<AnAction> list = expandActionGroupImpl(LaterInvocator.isInModalContext(), group, presentationFactory, context, place, true, removeIcon);
    boolean checked = group instanceof CheckedActionGroup;
    fillMenuInner(component, list, checked, enableMnemonics, presentationFactory, context, place, isWindowMenu, useDarkIcons);
  }

  static @NotNull Runnable addLoadingIcon(@Nullable RelativePoint point, @NotNull DataContext context, @NotNull String place) {
    JRootPane rootPane = point == null ? null : UIUtil.getRootPane(point.getComponent());
    JComponent glassPane = rootPane == null ? null : (JComponent)rootPane.getGlassPane();
    if (glassPane == null || !isAsyncDataContext(context)) return EmptyRunnable.getInstance();
    Component comp = point.getOriginalComponent();
    if (ActionPlaces.EDITOR_GUTTER_POPUP.equals(place) && comp instanceof EditorGutterComponentEx &&
        ((EditorGutterComponentEx)comp).getGutterRenderer(point.getOriginalPoint()) != null) {
      return EmptyRunnable.getInstance();
    }
    boolean isMenuItem = comp instanceof ActionMenu;
    JLabel icon = new JLabel(isMenuItem ? AnimatedIcon.Default.INSTANCE : AnimatedIcon.Big.INSTANCE);
    Dimension size = icon.getPreferredSize();
    icon.setSize(size);
    Point location = point.getPoint(glassPane);
    if (isMenuItem) {
      location.x -= 2 * size.width;
      location.y += (comp.getSize().height - size.height + 1) / 2;
    }
    else {
      location.x -= size.width / 2;
      location.y -= size.height / 2;
    }
    icon.setLocation(location);
    EdtScheduledExecutorService.getInstance().schedule(() -> {
      if (!icon.isVisible()) return;
      glassPane.add(icon);
    }, Registry.intValue("actionSystem.popup.progress.icon.delay", 500), TimeUnit.MILLISECONDS);
    return () -> {
      if (icon.getParent() != null) glassPane.remove(icon);
      else icon.setVisible(false);
    };
  }


  private static void fillMenuInner(@NotNull JComponent component,
                                    @NotNull List<? extends AnAction> list,
                                    boolean checked,
                                    boolean enableMnemonics,
                                    @NotNull PresentationFactory presentationFactory,
                                    @NotNull DataContext context,
                                    @NotNull String place,
                                    boolean isWindowMenu,
                                    boolean useDarkIcons) {
    component.removeAll();
    final boolean fixMacScreenMenu = SystemInfo.isMacSystemMenu && isWindowMenu && Registry.is("actionSystem.mac.screenMenuNotUpdatedFix");
    final ArrayList<Component> children = new ArrayList<>();

    for (int i = 0, size = list.size(); i < size; i++) {
      final AnAction action = list.get(i);
      Presentation presentation = presentationFactory.getPresentation(action);
      if (!(action instanceof Separator) && presentation.isVisible() && StringUtil.isEmpty(presentation.getText())) {
        String message = "Skipping empty menu item for action '" + action + "' (" + action.getClass()+")";
        if (action.getTemplatePresentation().getText() == null) {
          message += ". Please specify some default action text in plugin.xml or action constructor";
        }
        LOG.warn(message);
        continue;
      }

      if (action instanceof Separator) {
        final String text = ((Separator)action).getText();
        if (!StringUtil.isEmpty(text) || (i > 0 && i < size - 1)) {
          JPopupMenu.Separator separator = createSeparator(text);
          component.add(separator);
          children.add(separator);
        }
      }
      else if (action instanceof ActionGroup &&
               !Boolean.TRUE.equals(presentation.getClientProperty("actionGroup.perform.only"))) {
        ActionMenu menu = new ActionMenu(context, place, (ActionGroup)action, presentationFactory, enableMnemonics, useDarkIcons);
        component.add(menu);
        children.add(menu);
      }
      else {
        final ActionMenuItem each =
          new ActionMenuItem(action, presentation, place, context, enableMnemonics, !fixMacScreenMenu, checked, useDarkIcons);
        component.add(each);
        children.add(each);
      }
    }

    if (list.isEmpty()) {
      ActionMenuItem each = new ActionMenuItem(EMPTY_MENU_FILLER, presentationFactory.getPresentation(EMPTY_MENU_FILLER),
                                               place, context, enableMnemonics, !fixMacScreenMenu, checked, useDarkIcons);
      component.add(each);
      children.add(each);
    }

    if (fixMacScreenMenu) {
      SwingUtilities.invokeLater(() -> {
        for (Component each : children) {
          if (each.getParent() != null && each instanceof ActionMenuItem) {
            ((ActionMenuItem)each).prepare();
          }
        }
      });
    }
    if (SystemInfo.isMacSystemMenu && isWindowMenu) {
      if (ActionMenu.isAligned()) {
        Icon icon = hasIcons(children) ? ActionMenuItem.EMPTY_ICON : null;
        children.forEach(child -> replaceIconIn(child, icon));
      }
      else if (ActionMenu.isAlignedInGroup()) {
        ArrayList<Component> currentGroup = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
          Component child = children.get(i);
          boolean isSeparator = child instanceof JPopupMenu.Separator;
          boolean isLastElement = i == children.size() - 1;
          if (isLastElement || isSeparator) {
            if (isLastElement && !isSeparator) {
              currentGroup.add(child);
            }
            Icon icon = hasIcons(currentGroup) ? ActionMenuItem.EMPTY_ICON : null;
            currentGroup.forEach(menuItem -> replaceIconIn(menuItem, icon));
            currentGroup.clear();
          }
          else {
            currentGroup.add(child);
          }
        }
      }
    }
  }

  private static @NotNull JPopupMenu.Separator createSeparator(@NlsContexts.Separator String text) {
    return new JPopupMenu.Separator() {
      private final JMenuItem myMenu = !StringUtil.isEmpty(text) ? new JMenuItem(text) : null;

      @Override
      public void doLayout() {
        super.doLayout();
        if (myMenu != null) {
          myMenu.setBounds(getBounds());
        }
      }

      @Override
      protected void paintComponent(Graphics g) {
        if (StartupUiUtil.isUnderDarcula() || UIUtil.isUnderWin10LookAndFeel()) {
          g.setColor(getParent().getBackground());
          g.fillRect(0, 0, getWidth(), getHeight());
        }
        if (myMenu != null) {
          myMenu.paint(g);
        }
        else {
          super.paintComponent(g);
        }
      }

      @Override
      public Dimension getPreferredSize() {
        return myMenu != null ? myMenu.getPreferredSize() : super.getPreferredSize();
      }
    };
  }

  private static void replaceIconIn(Component menuItem, Icon icon) {
    Icon from = icon == null ? ActionMenuItem.EMPTY_ICON : null;

    if (menuItem instanceof ActionMenuItem && ((ActionMenuItem)menuItem).getIcon() == from) {
        ((ActionMenuItem)menuItem).setIcon(icon);
    } else if (menuItem instanceof ActionMenu && ((ActionMenu)menuItem).getIcon() == from) {
        ((ActionMenu)menuItem).setIcon(icon);
    }
  }

  private static boolean hasIcons(List<? extends Component> components) {
    for (Component comp : components) {
      if (hasNotEmptyIcon(comp)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasNotEmptyIcon(Component comp) {
    Icon icon = null;
    if (comp instanceof ActionMenuItem) {
      icon = ((ActionMenuItem)comp).getIcon();
    } else if (comp instanceof ActionMenu) {
      icon = ((ActionMenu)comp).getIcon();
    }

    return icon != null && icon != ActionMenuItem.EMPTY_ICON;
  }

  public static @NotNull UpdateSession getOrCreateUpdateSession(@NotNull AnActionEvent e) {
    UpdateSession updater = e.getUpdateSession();
    if (updater == null) {
      ActionUpdater actionUpdater = new ActionUpdater(
        LaterInvocator.isInModalContext(), new PresentationFactory(), e.getDataContext(),
        e.getPlace(), e.isFromContextMenu(), e.isFromActionToolbar());
      updater = actionUpdater.asUpdateSession();
    }
    return updater;
  }

  @ApiStatus.Internal
  public static @Nullable <T> T runUpdateSessionForInputEvent(@NotNull InputEvent inputEvent,
                                                              @NotNull DataContext dataContext,
                                                              @NotNull String place,
                                                              @NotNull ActionProcessor actionProcessor,
                                                              @NotNull PresentationFactory factory,
                                                              @Nullable Consumer<? super AnActionEvent> eventTracker,
                                                              @NotNull Function<? super UpdateSession, ? extends T> function) {
    long start = System.currentTimeMillis();
    boolean async = isAsyncDataContext(dataContext);
    // we will manually process "invokeLater" calls using a queue for performance reasons:
    // direct approach would be to pump events in a custom modality state (enterModal/leaveModal)
    // EventQueue would add significant overhead (x10), but key events must be processed ASAP.
    BlockingQueue<Runnable> queue = async ? new LinkedBlockingQueue<>() : null;
    ActionUpdater actionUpdater = new ActionUpdater(
      LaterInvocator.isInModalContext(), factory, dataContext,
      place, false, false, event -> {
        AnActionEvent transformed = actionProcessor.createEvent(
          inputEvent, event.getDataContext(), event.getPlace(), event.getPresentation(), event.getActionManager());
        if (eventTracker != null) eventTracker.accept(transformed);
        return transformed;
    }, async ? queue::offer : null);

    T result;
    if (async) {
      ActionUpdater.cancelAllUpdates();
      AsyncPromise<T> promise = new AsyncPromise<>();
      ActionUpdater.ourBeforePerformedExecutor.execute(() -> {
        try {
          Ref<T> ref = Ref.create();
          Ref<UpdateSession> sessionRef = Ref.create();
          ProgressManager.getInstance().computePrioritized(() -> {
            ProgressManager.getInstance().executeProcessUnderProgress(() -> {
              Set<String> missedKeys = ContainerUtil.newConcurrentSet();
              UpdateSession fastSession = actionUpdater.asFastUpdateSession(missedKeys::add, null);
              T fastResult = function.apply(fastSession);
              sessionRef.set(fastSession);
              if (fastResult != null) {
                ref.set(fastResult);
              }
              else if (tryInReadAction(() -> ContainerUtil.exists(missedKeys, o -> dataContext.getData(o) != null))) {
                UpdateSession slowSession = actionUpdater.asUpdateSession();
                T slowResult = function.apply(slowSession);
                ref.set(slowResult);
                sessionRef.set(slowSession);
              }
            }, new EmptyProgressIndicator());
            return ref.get();
          });
          queue.offer(() -> {
            ActionUpdater.getActionUpdater(sessionRef.get()).applyPresentationChanges();
            promise.setResult(ref.get());
          });
        }
        catch (Exception e) {
          promise.setError(e);
        }
      });
      result = runLoopAndWaitForFuture(promise, null, () -> {
        Runnable runnable = queue.poll(1, TimeUnit.MILLISECONDS);
        if (runnable != null) runnable.run();
      });
    }
    else {
      result = function.apply(actionUpdater.asUpdateSession());
      actionUpdater.applyPresentationChanges();
    }
    long time = System.currentTimeMillis() - start;
    if (time > 500) {
      LOG.debug("runUpdateSessionForKeyEvent() took: " + time + " ms");
    }
    return result;
  }

  private static <T> T runLoopAndWaitForFuture(@NotNull Future<? extends T> promise,
                                               @Nullable T defValue,
                                               @NotNull ThrowableRunnable<?> pumpRunnable) {
    while (!promise.isDone()) {
      try {
        pumpRunnable.run();
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
    try {
      return promise.isCancelled() ? defValue : promise.get();
    }
    catch (Exception ex) {
      Throwable cause = ExceptionUtil.getRootCause(ex);
      if (!(cause instanceof ProcessCanceledException)) {
        LOG.error(cause);
      }
    }
    return defValue;
  }

  @ApiStatus.Internal
  public static boolean tryInReadAction(@NotNull BooleanSupplier supplier) {
    boolean[] result = {false};
    ApplicationManagerEx.getApplicationEx().tryRunReadAction(() -> {
      result[0] = supplier.getAsBoolean();
    });
    return result[0];
  }

  public static boolean isFrozenDataContext(DataContext context) {
    return context instanceof PreCachedDataContext && ((PreCachedDataContext)context).isFrozenDataContext();
  }
}
