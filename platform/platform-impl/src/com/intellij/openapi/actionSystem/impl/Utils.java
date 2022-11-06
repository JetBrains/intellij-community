// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.CommonBundle;
import com.intellij.concurrency.SensitiveProgressWrapper;
import com.intellij.diagnostic.PluginException;
import com.intellij.diagnostic.telemetry.TraceManager;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ProhibitAWTEvents;
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.keymap.impl.ActionProcessor;
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.impl.IdeMenuBar;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.mac.screenmenu.Menu;
import com.intellij.util.*;
import com.intellij.util.concurrency.EdtScheduledExecutorService;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.CancellablePromise;
import org.jetbrains.concurrency.Promises;

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
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

@ApiStatus.Internal
public final class Utils {
  private static final Key<Boolean> IS_MODAL_CONTEXT = Key.create("Component.isModalContext");
  private static final Logger LOG = Logger.getInstance(Utils.class);

  public static final AnAction EMPTY_MENU_FILLER = new EmptyAction();
  static {
    EMPTY_MENU_FILLER.getTemplatePresentation().setText(CommonBundle.messagePointer("empty.menu.filler"));
  }

  static final AttributeKey<String> OT_OP_KEY = AttributeKey.stringKey("op");
  private static final ContextKey<Boolean> OT_ENABLE_SPANS = ContextKey.named("OT_ENABLE_SPANS");

  static @NotNull Tracer getTracer(boolean checkNoop) {
    return checkNoop && !Boolean.TRUE.equals(Context.current().get(OT_ENABLE_SPANS)) ?
           OpenTelemetry.noop().getTracer("") : TraceManager.INSTANCE.getTracer("actionSystem", true);
  }

  public static @NotNull DataContext wrapToAsyncDataContext(@NotNull DataContext dataContext) {
    if (isAsyncDataContext(dataContext)) {
      return dataContext;
    }
    else if (dataContext instanceof EdtDataContext) {
      Component component = dataContext.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
      return newPreCachedDataContext(component);
    }
    else if (dataContext instanceof CustomizedDataContext) {
      CustomizedDataContext context = (CustomizedDataContext)dataContext;
      DataContext delegate = wrapToAsyncDataContext(context.getParent());
      if (delegate == DataContext.EMPTY_CONTEXT) {
        return new PreCachedDataContext(null).prependProvider(context.getCustomDataProvider());
      }
      else if (delegate instanceof PreCachedDataContext) {
        return ((PreCachedDataContext)delegate).prependProvider(context.getCustomDataProvider());
      }
    }
    else if (!ApplicationManager.getApplication().isUnitTestMode()) { // see `HeadlessContext`
      LOG.warn(new Throwable("Unable to wrap '" + dataContext.getClass().getName() + "'. Use CustomizedDataContext or EdtDataContext"));
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
    return dataContext == DataContext.EMPTY_CONTEXT || dataContext instanceof AsyncDataContext;
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
  public static CancellablePromise<List<AnAction>> expandActionGroupAsync(@NotNull ActionGroup group,
                                                                          @NotNull PresentationFactory presentationFactory,
                                                                          @NotNull DataContext context,
                                                                          @NotNull String place) {
    return expandActionGroupAsync(group, presentationFactory, context, place, false, false);
  }

  @ApiStatus.Internal
  public static CancellablePromise<List<AnAction>> expandActionGroupAsync(@NotNull ActionGroup group,
                                                                          @NotNull PresentationFactory presentationFactory,
                                                                          @NotNull DataContext context,
                                                                          @NotNull String place,
                                                                          boolean isToolbarAction,
                                                                          boolean skipFastTrack) {
    LOG.assertTrue(isAsyncDataContext(context), "Async data context required in '" + place + "': " + context.getClass().getName());
    ActionUpdater updater = new ActionUpdater(presentationFactory, context, place, ActionPlaces.isPopupPlace(place), isToolbarAction);
    List<AnAction> actions = skipFastTrack ? null : expandActionGroupFastTrack(updater, group, group instanceof CompactActionGroup, null);
    if (actions != null) {
      return Promises.resolvedCancellablePromise(actions);
    }
    return updater.expandActionGroupAsync(group, group instanceof CompactActionGroup);
  }

  @ApiStatus.Internal
  public static List<AnAction> expandActionGroupWithTimeout(@NotNull ActionGroup group,
                                                            @NotNull PresentationFactory presentationFactory,
                                                            @NotNull DataContext context,
                                                            @NotNull String place,
                                                            int timeoutMs) {
    return new ActionUpdater(presentationFactory, context, place, false, false)
      .expandActionGroupWithTimeout(group, group instanceof CompactActionGroup, timeoutMs);
  }

  private static final boolean DO_FULL_EXPAND = Boolean.getBoolean("actionSystem.use.full.group.expand"); // for tests and debug

  /** @deprecated Use {@link #expandActionGroup(ActionGroup, PresentationFactory, DataContext, String)} */
  @Deprecated(forRemoval = true)
  public static @NotNull List<AnAction> expandActionGroup(boolean isInModalContext,
                                                          @NotNull ActionGroup group,
                                                          @NotNull PresentationFactory presentationFactory,
                                                          @NotNull DataContext context,
                                                          @NotNull String place) {
    return expandActionGroup(group, presentationFactory, context, place);
  }

  public static @NotNull List<AnAction> expandActionGroup(@NotNull ActionGroup group,
                                                          @NotNull PresentationFactory presentationFactory,
                                                          @NotNull DataContext context,
                                                          @NotNull String place) {
    RelativePoint point = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(context) == null ? null :
                          JBPopupFactory.getInstance().guessBestPopupLocation(context);
    Runnable removeIcon = addLoadingIcon(point, place);
    List<AnAction> result = null;
    Span span = getTracer(false).spanBuilder("expandActionGroup").setAttribute("place", place).startSpan();
    long start = System.nanoTime();
    try (Scope ignore = Context.current().with(span).with(OT_ENABLE_SPANS, true).makeCurrent()) {
      return result = computeWithRetries(
        () -> expandActionGroupImpl(group, presentationFactory, context, place, ActionPlaces.isPopupPlace(place), removeIcon, null),
        null, removeIcon);
    }
    finally {
      long elapsed = TimeoutUtil.getDurationMillis(start);
      span.end();
      if (elapsed > 1000) {
        LOG.warn(elapsed + " ms to expandActionGroup@" + place);
      }
      ActionsCollectorImpl.recordActionGroupExpanded(group, context, place, false, elapsed, result);
    }
  }

  private static int ourExpandActionGroupImplEDTLoopLevel;

  private static @NotNull List<AnAction> expandActionGroupImpl(@NotNull ActionGroup group,
                                                               @NotNull PresentationFactory presentationFactory,
                                                               @NotNull DataContext context,
                                                               @NotNull String place,
                                                               boolean isContextMenu,
                                                               @Nullable Runnable onProcessed,
                                                               @Nullable JComponent menuItem) {
    boolean isUnitTestMode = ApplicationManager.getApplication().isUnitTestMode();
    DataContext wrapped = wrapDataContext(context);
    Project project = CommonDataKeys.PROJECT.getData(wrapped);
    Component contextComponent = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(wrapped);
    ActionUpdater updater = new ActionUpdater(presentationFactory, wrapped, place, isContextMenu, false, null, null);
    ActionGroupExpander expander = ActionGroupExpander.getInstance();
    List<AnAction> list;
    if (isAsyncDataContext(wrapped) && !isUnitTestMode) {
      if (isContextMenu) {
        ActionUpdater.cancelAllUpdates("context menu requested");
      }
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
      CancellablePromise<List<AnAction>> promise = updater.expandActionGroupAsync(group, group instanceof CompactActionGroup);
      if (onProcessed != null) {
        promise.onSuccess(__ -> onProcessed.run());
        promise.onError(ex -> {
          if (!canRetryOnThisException(ex)) onProcessed.run();
        });
      }
      try (AccessToken ignore = cancelOnUserActivityInside(promise, contextComponent, menuItem)) {
        ourExpandActionGroupImplEDTLoopLevel++;
        list = runLoopAndWaitForFuture(promise, Collections.emptyList(), true, () -> {
          AWTEvent event = queue.getNextEvent();
          queue.dispatchEvent(event);
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
      if (Registry.is("actionSystem.update.actions.async") && !isUnitTestMode) {
        LOG.error("Async data context required in '" + place + "': " + wrapped.getClass().getName());
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
                                                             @Nullable Consumer<? super String> missedKeys) {
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
    List<AnAction> result = null;
    Span span = getTracer(false).spanBuilder("fillMenu").setAttribute("place", place).startSpan();
    long start = System.nanoTime();
    try (Scope ignore = Context.current().with(span).with(OT_ENABLE_SPANS, true).makeCurrent()) {
      Runnable removeIcon = addLoadingIcon(progressPoint, place);
      result = computeWithRetries(
        () -> expandActionGroupImpl(group, presentationFactory, context, place, true, removeIcon, component),
        expire, removeIcon);
      boolean checked = group instanceof CheckedActionGroup;
      boolean multiChoice = isMultiChoiceGroup(group);
      fillMenuInner(component, result, checked, multiChoice, enableMnemonics, presentationFactory, context, place, isWindowMenu, useDarkIcons);
    }
    finally {
      long elapsed = TimeoutUtil.getDurationMillis(start);
      span.end();
      if (elapsed > 1000) {
        LOG.warn(elapsed + " ms to fillMenu@" + place);
      }
      boolean submenu = component instanceof ActionMenu && component.getParent() != null;
      ActionsCollectorImpl.recordActionGroupExpanded(group, context, place, submenu, elapsed, result);
    }
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
                                    boolean multiChoice,
                                    boolean enableMnemonics,
                                    @NotNull PresentationFactory presentationFactory,
                                    @NotNull DataContext context,
                                    @NotNull String place,
                                    boolean isWindowMenu,
                                    boolean useDarkIcons) {
    component.removeAll();
    Menu nativePeer = component instanceof ActionMenu ? ((ActionMenu)component).getScreenMenuPeer() : null;
    if (nativePeer != null) nativePeer.beginFill();
    ArrayList<Component> children = new ArrayList<>();

    for (int i = 0, size = list.size(); i < size; i++) {
      AnAction action = list.get(i);
      Presentation presentation = presentationFactory.getPresentation(action);
      if (!presentation.isVisible()) {
        reportInvisibleMenuItem(action, place);
        continue;
      }
      else if (!(action instanceof Separator) && StringUtil.isEmpty(presentation.getText())) {
        reportEmptyTextMenuItem(action, place);
        continue;
      }
      if (multiChoice && action instanceof Toggleable) {
        presentation.setMultiChoice(true);
      }

      if (action instanceof Separator) {
        String text = ((Separator)action).getText();
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
        ActionMenuItem each = new ActionMenuItem(action, place, context, enableMnemonics, checked, useDarkIcons);
        each.updateFromPresentation(presentation);
        component.add(each);
        children.add(each);
        if (nativePeer != null) nativePeer.add(each.getScreenMenuItemPeer());
      }
    }

    if (list.isEmpty()) {
      Presentation presentation = presentationFactory.getPresentation(EMPTY_MENU_FILLER);
      ActionMenuItem each = new ActionMenuItem(EMPTY_MENU_FILLER, place, context, enableMnemonics, checked, useDarkIcons);
      each.updateFromPresentation(presentation);
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

  private static void reportInvisibleMenuItem(@NotNull AnAction action, @NotNull String place) {
    String operationName = operationName(action, null, place);
    LOG.error("Invisible menu item for " + operationName);
  }

  private static void reportEmptyTextMenuItem(@NotNull AnAction action, @NotNull String place) {
    String operationName = operationName(action, null, place);
    String message = "Empty menu item text for " + operationName;
    if (StringUtil.isEmpty(action.getTemplatePresentation().getText())) {
      message += ". The default action text must be specified in plugin.xml or its class constructor";
    }
    LOG.error(PluginException.createByClass(message, null, action.getClass()));
  }

  public static @NotNull String operationName(@NotNull Object action, @Nullable String op, @Nullable String place) {
    Class<?> c = action.getClass();
    StringBuilder sb = new StringBuilder(200);
    if (StringUtil.isNotEmpty(op)) sb.append("#").append(op);
    if (StringUtil.isNotEmpty(place)) sb.append("@").append(place);
    sb.append(" (");
    for (Object x = action; x instanceof ActionWithDelegate; x = ((ActionWithDelegate<?>)x).getDelegate(), c = x.getClass()) {
      sb.append(c.getSimpleName()).append("/");
    }
    sb.append(c.getName()).append(")");
    sb.insert(0, StringUtil.isNotEmpty(c.getSimpleName()) ? c.getSimpleName() : StringUtil.getShortName(c.getName()));
    return sb.toString();
  }

  public static boolean isMultiChoiceGroup(@NotNull ActionGroup actionGroup) {
    Presentation p = actionGroup.getTemplatePresentation();
    if (p.isMultiChoice()) return true;
    if (p.getIcon() == AllIcons.Actions.GroupBy ||
        p.getIcon() == AllIcons.Actions.Show ||
        p.getIcon() == AllIcons.General.GearPlain ||
        p.getIcon() == AllIcons.Debugger.RestoreLayout) {
      return true;
    }
    if (actionGroup.getClass() == DefaultActionGroup.class) {
      for (AnAction child : actionGroup.getChildren(null)) {
        if (child instanceof Separator) continue;
        if (!(child instanceof Toggleable)) return false;
      }
      return true;
    }
    return false;
  }

  static void updateMenuItems(@NotNull JPopupMenu popupMenu,
                              @NotNull DataContext dataContext,
                              @NotNull String place,
                              @NotNull PresentationFactory presentationFactory) {
    List<ActionMenuItem> items = ContainerUtil.filterIsInstance(popupMenu.getComponents(), ActionMenuItem.class);
    updateComponentActions(
      popupMenu, ContainerUtil.map(items, ActionMenuItem::getAnAction), dataContext, place, presentationFactory,
      () -> {
        for (ActionMenuItem item : items) {
          item.updateFromPresentation(presentationFactory.getPresentation(item.getAnAction()));
        }
      });
  }

  @ApiStatus.Internal
  public static void updateComponentActions(@NotNull JComponent component,
                                            @NotNull Iterable<? extends AnAction> actions,
                                            @NotNull DataContext dataContext,
                                            @NotNull String place,
                                            @NotNull PresentationFactory presentationFactory,
                                            @NotNull Runnable onUpdate) {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    for (AnAction action : actions) {
      actionGroup.add(action);
    }
    // note that no retries are attempted
    if (isAsyncDataContext(dataContext)) {
      expandActionGroupAsync(actionGroup, presentationFactory, dataContext, place)
        .onSuccess(__ -> {
          try {
            onUpdate.run();
          }
          finally {
            component.repaint();
          }
        });
    }
    else {
      expandActionGroupImpl(actionGroup, presentationFactory, dataContext, place, ActionPlaces.isPopupPlace(place), null, null);
      onUpdate.run();
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

  /**
   * Check if the {@code component} represents a modal context in a general sense,
   * i.e., whether any of its parents is either a modal {@link Window}
   * or explicitly marked to be treated like a modal context.
   * @see Utils#markAsModalContext(JComponent, Boolean)
   */
  @ApiStatus.Internal
  public static boolean isModalContext(@NotNull Component component) {
    Boolean implicitValue = IdeKeyEventDispatcher.isModalContextOrNull(component);
    if (implicitValue != null) {
      return implicitValue;
    }
    do {
      Boolean explicitValue = ClientProperty.get(component, IS_MODAL_CONTEXT);
      if (explicitValue != null) {
        return explicitValue;
      }
      component = component.getParent();
    } while (component != null);
    return true;
  }

  /**
   * Mark the {@code component} to be treated like a modal context (or not) when it cannot be deduced implicitly from UI hierarchy.
   * @param isModalContext {@code null} to clear a mark, to set a new one otherwise.
   * @see Utils#isModalContext(Component)
   */
  @ApiStatus.Internal
  public static void markAsModalContext(@NotNull JComponent component, @Nullable Boolean isModalContext) {
    ClientProperty.put(component, IS_MODAL_CONTEXT, isModalContext);
  }

  public static @NotNull UpdateSession getOrCreateUpdateSession(@NotNull AnActionEvent e) {
    UpdateSession updater = e.getUpdateSession();
    if (updater == null) {
      ActionUpdater actionUpdater = new ActionUpdater(
        new PresentationFactory(), e.getDataContext(),
        e.getPlace(), e.isFromContextMenu(), e.isFromActionToolbar());
      updater = actionUpdater.asUpdateSession();
    }
    return updater;
  }

  private static boolean ourInUpdateSessionForInputEventEDTLoop;

  @ApiStatus.Internal
  public static @Nullable <T> T runUpdateSessionForInputEvent(@NotNull List<AnAction> actions,
                                                              @NotNull InputEvent inputEvent,
                                                              @NotNull DataContext dataContext,
                                                              @NotNull String place,
                                                              @NotNull ActionProcessor actionProcessor,
                                                              @NotNull PresentationFactory factory,
                                                              @Nullable Consumer<? super AnActionEvent> eventTracker,
                                                              @NotNull BiFunction<? super UpdateSession, ? super List<AnAction>, ? extends T> function) {
    ApplicationEx applicationEx = ApplicationManagerEx.getApplicationEx();
    if (ProgressIndicatorUtils.isWriteActionRunningOrPending(applicationEx)) {
      LOG.error("Actions cannot be updated when write-action is running or pending");
      return null;
    }
    if (ourInUpdateSessionForInputEventEDTLoop) {
      LOG.warn("Recursive shortcut processing invocation is ignored");
      return null;
    }
    long start = System.nanoTime();
    boolean async = isAsyncDataContext(dataContext);
    // we will manually process "invokeLater" calls using a queue for performance reasons:
    // direct approach would be to pump events in a custom modality state (enterModal/leaveModal)
    // EventQueue would add significant overhead (x10), but key events must be processed ASAP.
    BlockingQueue<Runnable> queue = async ? new LinkedBlockingQueue<>() : null;
    ActionUpdater actionUpdater = new ActionUpdater(
      factory, dataContext,
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
          ThrowableComputable<Void, RuntimeException> computable = () -> {
            List<AnAction> adjusted = new ArrayList<>(actions);
            actionUpdater.tryRunReadActionAndCancelBeforeWrite(promise, () -> rearrangeByPromoters(adjusted, dataContext));
            if (promise.isDone()) return null;
            UpdateSession session = actionUpdater.asUpdateSession();
            actionUpdater.tryRunReadActionAndCancelBeforeWrite(promise, () -> ref.set(function.apply(session, adjusted)));
            queue.offer(actionUpdater::applyPresentationChanges);
            return null;
          };
          ProgressIndicator indicator = parentIndicator == null ? new ProgressIndicatorBase() : new SensitiveProgressWrapper(parentIndicator);
          promise.onError(__ -> indicator.cancel());
          ProgressManager.getInstance().executeProcessUnderProgress(
            () -> ProgressManager.getInstance().computePrioritized(computable),
            indicator);
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
      List<AnAction> adjusted = new ArrayList<>(actions);
      rearrangeByPromoters(adjusted, freezeDataContext(dataContext, null));
      result = function.apply(actionUpdater.asUpdateSession(), adjusted);
      actionUpdater.applyPresentationChanges();
    }
    long elapsed = TimeoutUtil.getDurationMillis(start);
    if (elapsed > 1000) {
      LOG.warn(elapsed + " ms to runUpdateSessionForInputEvent@" + place);
    }
    return result;
  }

  @ApiStatus.Internal
  public static void rearrangeByPromoters(@NotNull List<AnAction> actions, @NotNull DataContext dataContext) {
    DataContext frozenContext = freezeDataContext(dataContext, null);
    List<AnAction> readOnlyActions = Collections.unmodifiableList(actions);
    List<ActionPromoter> promoters = ContainerUtil.concat(
      ActionPromoter.EP_NAME.getExtensionList(), ContainerUtil.filterIsInstance(actions, ActionPromoter.class));
    for (ActionPromoter promoter : promoters) {
      try (AccessToken ignore = SlowOperations.allowSlowOperations(SlowOperations.FORCE_ASSERT)) {
        List<AnAction> promoted = promoter.promote(readOnlyActions, frozenContext);
        if (promoted != null && !promoted.isEmpty()) {
          actions.removeAll(promoted);
          actions.addAll(0, promoted);
        }
        List<AnAction> suppressed = promoter.suppress(readOnlyActions, frozenContext);
        if (suppressed != null && !suppressed.isEmpty()) {
          actions.removeAll(suppressed);
        }
      }
      catch (ProcessCanceledException ignore) {
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
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
      ProcessCanceledException pce = ExceptionUtilRt.findCause(ex, ProcessCanceledException.class);
      if (pce == null) {
        LOG.error(ex);
      }
      else if (rethrowCancellation) {
        pce.fillInStackTrace();
        throw pce;
      }
    }
    return defValue;
  }

  @RequiresEdt
  private static <T> T computeWithRetries(@NotNull Supplier<? extends T> computable, @Nullable BooleanSupplier expire, @Nullable Runnable onProcessed) {
    ProcessCanceledWithReasonException lastCancellation = null;
    int retries = Math.max(1, Registry.intValue("actionSystem.update.actions.max.retries", 20));
    for (int i = 0; i < retries; i++) {
      try (AccessToken ignore = SlowOperations.allowSlowOperations(SlowOperations.RESET)) {
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