// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.CommonBundle;
import com.intellij.concurrency.SensitiveProgressWrapper;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ProhibitAWTEvents;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.keymap.impl.ActionProcessor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.impl.IdeMenuBar;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.mac.screenmenu.Menu;
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
import java.util.function.Supplier;

@ApiStatus.Internal
public final class Utils {
  private static final Logger LOG = Logger.getInstance(Utils.class);

  public static final AnAction EMPTY_MENU_FILLER = new EmptyAction();
  static {
    EMPTY_MENU_FILLER.getTemplatePresentation().setText(CommonBundle.messagePointer("empty.menu.filler"));
  }

  public static @NotNull DataContext wrapToAsyncDataContext(@NotNull DataContext dataContext) {
    Component component = dataContext.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
    if (dataContext instanceof EdtDataContext) {
      return newPreCachedDataContext(component);
    }
    else if (dataContext instanceof SimpleDataContext && component != null) {
      DataContext wrapped = newPreCachedDataContext(component);
      LOG.assertTrue(wrapped.getData(CommonDataKeys.PROJECT) == dataContext.getData(CommonDataKeys.PROJECT));
      LOG.warn(new Throwable("Use DataManager.getDataContext(component) instead of SimpleDataContext for wrapping."));
      return wrapped;
    }
    return dataContext;
  }

  private static @NotNull DataContext newPreCachedDataContext(@Nullable Component component) {
    return new PreCachedDataContext(component);
  }

  public static @NotNull DataContext wrapDataContext(@NotNull DataContext dataContext) {
    if (!Registry.is("actionSystem.update.actions.async")) return dataContext;
    return wrapToAsyncDataContext(dataContext);
  }

  @ApiStatus.Internal
  public static @NotNull DataContext freezeDataContext(@NotNull DataContext dataContext, @Nullable Consumer<? super String> missedKeys) {
    return dataContext instanceof PreCachedDataContext ? ((PreCachedDataContext)dataContext).frozenCopy(missedKeys) :
           dataContext;
  }

  public static boolean isAsyncDataContext(@NotNull DataContext dataContext) {
    return dataContext instanceof AsyncDataContext;
  }

  @ApiStatus.Internal
  public static @Nullable Object getRawDataIfCached(@NotNull DataContext dataContext, @NotNull String dataId) {
    return dataContext instanceof PreCachedDataContext ? ((PreCachedDataContext)dataContext).getRawDataIfCached(dataId) :
           dataContext instanceof EdtDataContext ? ((EdtDataContext)dataContext).getRawDataIfCached(dataId) : null;
  }

  static void clearAllCachesAndUpdates() {
    ActionUpdater.cancelAllUpdates("clear-all-caches-and-updates requested");
    ActionUpdater.waitForAllUpdatesToFinish();
    PreCachedDataContext.clearAllCaches();
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
    RelativePoint point = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(context) == null ? null :
                          JBPopupFactory.getInstance().guessBestPopupLocation(context);
    Runnable removeIcon = addLoadingIcon(point, place);
    return computeWithRetries(
      () -> expandActionGroupImpl(isInModalContext, group, presentationFactory,
                                  context, place, ActionPlaces.isPopupPlace(place), removeIcon, null),
      null, removeIcon);
  }

  private static int ourExpandActionGroupImplEDTLoopLevel;

  private static @NotNull List<AnAction> expandActionGroupImpl(boolean isInModalContext,
                                                               @NotNull ActionGroup group,
                                                               @NotNull PresentationFactory presentationFactory,
                                                               @NotNull DataContext context,
                                                               @NotNull String place,
                                                               boolean isContextMenu,
                                                               @Nullable Runnable onProcessed,
                                                               @Nullable JComponent menuItem) {
    boolean async = isAsyncDataContext(context);
    boolean asyncUI = async && Registry.is("actionSystem.update.actions.async.ui");
    BlockingQueue<Runnable> queue0 = async && !asyncUI ? new LinkedBlockingQueue<>() : null;
    ActionUpdater updater = new ActionUpdater(
      isInModalContext, presentationFactory, context, place, isContextMenu, false, null, queue0 != null ? queue0::offer : null);
    ActionGroupExpander expander = ActionGroupExpander.getInstance();
    Project project = CommonDataKeys.PROJECT.getData(context);
    List<AnAction> list;
    if (async) {
      if (expander.allowsFastUpdate(project, place) && !Registry.is("actionSystem.update.actions.suppress.dataRules.on.edt")) {
        Set<String> missedKeys = new HashSet<>();
        list = expandActionGroupFastTrack(updater, group, group instanceof CompactActionGroup, missedKeys::add);
        if (list != null && missedKeys.isEmpty()) {
          if (onProcessed != null) onProcessed.run();
          return list;
        }
      }
      int maxLoops = Math.max(2, Registry.intValue("actionSystem.update.actions.async.max.nested.loops", 20));
      if (ourExpandActionGroupImplEDTLoopLevel >= maxLoops) {
        LOG.warn("Maximum number of recursive EDT loops reached (" + maxLoops +") at '" + place + "'");
        if (onProcessed != null) onProcessed.run();
        ActionUpdater.cancelAllUpdates("recursive EDT loops limit reached at '" + place + "'");
        throw new ProcessCanceledException();
      }
      IdeEventQueue queue = IdeEventQueue.getInstance();
      CancellablePromise<List<AnAction>> promise = expander.expandActionGroupAsync(
        project, place, group, group instanceof CompactActionGroup, updater::expandActionGroupAsync);
      if (onProcessed != null) {
        promise.onSuccess(__ -> onProcessed.run());
        promise.onError(ex -> {
          if (!canRetryOnThisException(ex)) onProcessed.run();
        });
      }
      try (AccessToken ignore = cancelOnUserActivityInside(promise, PlatformDataKeys.CONTEXT_COMPONENT.getData(context), menuItem)) {
        ourExpandActionGroupImplEDTLoopLevel++;
        list = runLoopAndWaitForFuture(promise, Collections.emptyList(), true, () -> {
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
      finally {
        ourExpandActionGroupImplEDTLoopLevel--;
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

  private static @NotNull AccessToken cancelOnUserActivityInside(@NotNull CancellablePromise<List<AnAction>> promise,
                                                                 @Nullable Component contextComponent,
                                                                 @Nullable Component menuItem) {
    Window window = contextComponent == null ? null : SwingUtilities.getWindowAncestor(contextComponent);
    return ProhibitAWTEvents.startFiltered("expandActionGroup", event -> {
      if (event instanceof FocusEvent && event.getID() == FocusEvent.FOCUS_LOST &&
          ((FocusEvent)event).getCause() == FocusEvent.Cause.ACTIVATION &&
           window != null && window == SwingUtilities.getWindowAncestor(((FocusEvent)event).getComponent()) ||
          event instanceof KeyEvent && event.getID() == KeyEvent.KEY_PRESSED ||
          event instanceof MouseEvent && event.getID() == MouseEvent.MOUSE_PRESSED && UIUtil.getDeepestComponentAt(
            ((MouseEvent)event).getComponent(), ((MouseEvent)event).getX(), ((MouseEvent)event).getY()) != menuItem ) {
        ActionUpdater.cancelPromise(promise, event);
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
      CancellablePromise<List<AnAction>> promise = fastUpdater.expandActionGroupAsync(group, hideDisabled);
      return runLoopAndWaitForFuture(promise, null, false, () -> {
        Runnable runnable = queue.poll(1, TimeUnit.MILLISECONDS);
        if (runnable != null) runnable.run();
        long elapsed = System.currentTimeMillis() - start;
        if (elapsed > maxTime) {
          ActionUpdater.cancelPromise(promise, "fast-track timed out");
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
                       @Nullable RelativePoint progressPoint,
                       @Nullable BooleanSupplier expire) {
    if (ApplicationManagerEx.getApplicationEx().isWriteActionInProgress()) {
      throw new ProcessCanceledException();
    }
    Runnable removeIcon = addLoadingIcon(progressPoint, place);
    List<AnAction> list = computeWithRetries(
      () -> expandActionGroupImpl(LaterInvocator.isInModalContext(), group, presentationFactory,
                                  context, place, true, removeIcon, component),
      expire, removeIcon);
    boolean checked = group instanceof CheckedActionGroup;
    fillMenuInner(component, list, checked, enableMnemonics, presentationFactory, context, place, isWindowMenu, useDarkIcons);
  }

  private static @NotNull Runnable addLoadingIcon(@Nullable RelativePoint point, @NotNull String place) {
    if (!Registry.is("actionSystem.update.actions.async")) return EmptyRunnable.getInstance();
    JRootPane rootPane = point == null ? null : UIUtil.getRootPane(point.getComponent());
    JComponent glassPane = rootPane == null ? null : (JComponent)rootPane.getGlassPane();
    if (glassPane == null) return EmptyRunnable.getInstance();
    Component comp = point.getOriginalComponent();
    if (comp instanceof ActionMenu && comp.getParent() instanceof IdeMenuBar ||
        ActionPlaces.EDITOR_GUTTER_POPUP.equals(place) && comp instanceof EditorGutterComponentEx &&
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
    final @Nullable Menu nativePeer = component instanceof ActionMenu ? ((ActionMenu)component).getScreenMenuPeer() : null;
    if (nativePeer != null) nativePeer.beginFill();
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
          if (nativePeer != null) nativePeer.add(null);
        }
      }
      else if (action instanceof ActionGroup && !isSubmenuSuppressed(presentation)) {
        ActionMenu menu = new ActionMenu(context, place, (ActionGroup)action, presentationFactory, enableMnemonics, useDarkIcons);
        component.add(menu);
        children.add(menu);
        if (nativePeer != null) nativePeer.add(menu.getScreenMenuPeer());
      }
      else {
        ActionMenuItem each = new ActionMenuItem(action, presentation, place, context, enableMnemonics, true, checked, useDarkIcons);
        component.add(each);
        children.add(each);
        if (nativePeer != null) nativePeer.add(each.getScreenMenuItemPeer());
      }
    }

    if (list.isEmpty()) {
      ActionMenuItem each = new ActionMenuItem(EMPTY_MENU_FILLER, presentationFactory.getPresentation(EMPTY_MENU_FILLER),
                                               place, context, enableMnemonics, true, checked, useDarkIcons);
      component.add(each);
      children.add(each);
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

  public static boolean isSubmenuSuppressed(@NotNull Presentation presentation) {
    return Boolean.TRUE.equals(presentation.getClientProperty(ActionUpdater.SUPPRESS_SUBMENU_IMPL));
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

  private static boolean ourInUpdateSessionForInputEventEDTLoop;

  @ApiStatus.Internal
  public static @Nullable <T> T runUpdateSessionForInputEvent(@NotNull InputEvent inputEvent,
                                                              @NotNull DataContext dataContext,
                                                              @NotNull String place,
                                                              @NotNull ActionProcessor actionProcessor,
                                                              @NotNull PresentationFactory factory,
                                                              @Nullable Consumer<? super AnActionEvent> eventTracker,
                                                              @NotNull Function<? super UpdateSession, ? extends T> function) {
    ApplicationEx applicationEx = ApplicationManagerEx.getApplicationEx();
    if (ProgressIndicatorUtils.isWriteActionRunningOrPending(applicationEx)) {
      LOG.error("Actions cannot be updated when write-action is running or pending");
      return null;
    }
    if (ourInUpdateSessionForInputEventEDTLoop) {
      LOG.warn("Recursive shortcut processing invocation is ignored");
      return null;
    }
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
      ActionUpdater.cancelAllUpdates("'" + place + "' invoked");
      AsyncPromise<T> promise = ActionUpdater.newPromise(place);
      ProgressIndicator parentIndicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
      ActionUpdater.ourBeforePerformedExecutor.execute(() -> {
        try {
          Ref<T> ref = Ref.create();
          Ref<UpdateSession> sessionRef = Ref.create();
          Runnable runnable = () -> {
            Set<String> missedKeys = Registry.is("actionSystem.update.actions.suppress.dataRules.on.edt") ? null : ContainerUtil.newConcurrentSet();
            if (missedKeys != null) {
              UpdateSession fastSession = actionUpdater.asFastUpdateSession(missedKeys::add, null);
              T fastResult = function.apply(fastSession);
              ref.set(fastResult);
              sessionRef.set(fastSession);
            }
            if (ref.isNull() && (missedKeys == null || tryInReadAction(() -> ContainerUtil.exists(missedKeys, o -> dataContext.getData(o) != null)))) {
              UpdateSession slowSession = actionUpdater.asUpdateSession();
              T slowResult = function.apply(slowSession);
              ref.set(slowResult);
              sessionRef.set(slowSession);
            }
          };
          ProgressIndicator indicator = parentIndicator == null ? new ProgressIndicatorBase() : new SensitiveProgressWrapper(parentIndicator);
          promise.onError(__ -> indicator.cancel());
          ProgressManager.getInstance().computePrioritized(() -> {
            ProgressManager.getInstance().executeProcessUnderProgress(() ->
              ProgressIndicatorUtils.runActionAndCancelBeforeWrite(
                applicationEx,
                () -> ActionUpdater.cancelPromise(promise, "nested write-action requested"),
                () -> applicationEx.tryRunReadAction(runnable)), indicator);
            return ref.get();
          });
          queue.offer(ActionUpdater.getActionUpdater(sessionRef.get())::applyPresentationChanges);
          queue.offer(() -> promise.setResult(ref.get()));
        }
        catch (Throwable e) {
          promise.setError(e);
        }
      });
      try {
        ourInUpdateSessionForInputEventEDTLoop = true;
        result = runLoopAndWaitForFuture(promise, null, false, () -> {
          Runnable runnable = queue.poll(1, TimeUnit.MILLISECONDS);
          if (runnable != null) runnable.run();
          if (parentIndicator != null) parentIndicator.checkCanceled();
        });
      }
      finally {
        ourInUpdateSessionForInputEventEDTLoop = false;
      }
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
                                               boolean rethrowCancellation,
                                               @NotNull ThrowableRunnable<?> pumpRunnable) {
    while (!promise.isDone()) {
      try {
        pumpRunnable.run();
      }
      catch (ProcessCanceledException ignore) {
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
    try {
      return promise.isCancelled() ? defValue : promise.get();
    }
    catch (Throwable ex) {
      Throwable cause = ExceptionUtil.getRootCause(ex);
      if (!(cause instanceof ProcessCanceledException)) {
        LOG.error(cause);
      }
      else if (rethrowCancellation) {
        cause.fillInStackTrace();
        throw (ProcessCanceledException)cause;
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

  private static <T> T computeWithRetries(@NotNull Supplier<T> computable, @Nullable BooleanSupplier expire, @Nullable Runnable onProcessed) {
    ProcessCanceledWithReasonException lastCancellation = null;
    int retries = Math.max(1, Registry.intValue("actionSystem.update.actions.max.retries", 20));
    for (int i = 0; i < retries; i++) {
      try {
        return computable.get();
      }
      catch (Utils.ProcessCanceledWithReasonException ex) {
        lastCancellation = ex;
        if (canRetryOnThisException(ex) &&
            (expire == null || !expire.getAsBoolean())) {
          continue;
        }
        throw ex;
      }
      catch (Throwable ex) {
        ExceptionUtil.rethrow(ex);
      }
      finally {
        if (onProcessed != null) {
          onProcessed.run();
        }
      }
    }
    if (retries > 1) {
      LOG.warn("Maximum number of retries to show a menu reached (" + retries + "): " + lastCancellation.reason);
    }
    throw Objects.requireNonNull(lastCancellation);
  }

  private static boolean canRetryOnThisException(Throwable ex) {
    Object reason = ex instanceof ProcessCanceledWithReasonException ? ((ProcessCanceledWithReasonException)ex).reason : null;
    String reasonStr = reason instanceof String ? (String)reason : "";
    return reasonStr.contains("write-action") ||
           reasonStr.contains("fast-track") && StringUtil.containsIgnoreCase(reasonStr, "toolbar");
  }

  static class ProcessCanceledWithReasonException extends ProcessCanceledException {
    final Object reason;

    ProcessCanceledWithReasonException(Object reason) {
      this.reason = reason;
    }
  }
}