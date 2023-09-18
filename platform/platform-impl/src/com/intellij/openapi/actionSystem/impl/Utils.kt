// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.CommonBundle
import com.intellij.concurrency.SensitiveProgressWrapper
import com.intellij.concurrency.resetThreadContext
import com.intellij.diagnostic.PluginException
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.ProhibitAWTEvents
import com.intellij.ide.ui.UISettings
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl.Companion.recordActionGroupExpanded
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionMenu.Companion.isAligned
import com.intellij.openapi.actionSystem.impl.ActionMenu.Companion.isAlignedInGroup
import com.intellij.openapi.actionSystem.util.ActionSystem
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.keymap.impl.ActionProcessor
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.ide.menu.IdeJMenuBar
import com.intellij.platform.ide.menu.MacNativeActionMenuItem
import com.intellij.platform.ide.menu.createMacNativeActionMenu
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ClientProperty
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.GroupHeaderSeparator
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.mac.screenmenu.Menu
import com.intellij.ui.mac.screenmenu.MenuItem
import com.intellij.util.ExceptionUtilRt
import com.intellij.util.SlowOperations
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.EdtScheduledExecutorService
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.StartupUiUtil.isUnderDarcula
import com.intellij.util.ui.UIUtil
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.CancellablePromise
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Window
import java.awt.event.FocusEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.*
import java.util.concurrent.*
import java.util.function.Consumer
import javax.swing.*
import kotlin.math.max

internal val EMPTY_MENU_ACTION_ICON: Icon = EmptyIcon.create(16, 1)

private val IS_MODAL_CONTEXT = Key.create<Boolean>("Component.isModalContext")
private val OT_ENABLE_SPANS: ContextKey<Boolean> = ContextKey.named("OT_ENABLE_SPANS")

// for tests and debug
private val DO_FULL_EXPAND = java.lang.Boolean.getBoolean("actionSystem.use.full.group.expand")

private val LOG = logger<Utils>()

@ApiStatus.Internal
object Utils {
  @JvmField
  val EMPTY_MENU_FILLER: AnAction = EmptyAction().apply {
    getTemplatePresentation().setText(CommonBundle.messagePointer("empty.menu.filler"))
  }

  internal val OT_OP_KEY: AttributeKey<String> = AttributeKey.stringKey("op")

  internal fun getTracer(checkNoop: Boolean): Tracer {
    return if (checkNoop && Context.current().get(OT_ENABLE_SPANS) != true) {
      OpenTelemetry.noop().getTracer("")
    }
    else {
      TelemetryManager.getInstance().getTracer(ActionSystem)
    }
  }

  @JvmStatic
  fun wrapToAsyncDataContext(dataContext: DataContext): DataContext = when {
    isAsyncDataContext(dataContext) -> dataContext
    dataContext is EdtDataContext -> newPreCachedDataContext(dataContext.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT))
    dataContext is CustomizedDataContext ->
      when (val delegate = wrapToAsyncDataContext(dataContext.getParent())) {
        DataContext.EMPTY_CONTEXT -> PreCachedDataContext(null)
          .prependProvider(dataContext.customDataProvider)
        is PreCachedDataContext -> delegate
          .prependProvider(dataContext.customDataProvider)
        else -> dataContext
      }
    !ApplicationManager.getApplication().isUnitTestMode() -> { // see `HeadlessContext`
      LOG.warn(Throwable("Unable to wrap '" + dataContext.javaClass.getName() + "'. Use CustomizedDataContext or EdtDataContext"))
      dataContext
    }
    else -> dataContext
  }

  private fun newPreCachedDataContext(component: Component?): DataContext = PreCachedDataContext(component)

  @JvmStatic
  fun wrapDataContext(dataContext: DataContext): DataContext {
    return if (Registry.`is`("actionSystem.update.actions.async", true)) wrapToAsyncDataContext(dataContext) else dataContext
  }

  @JvmStatic
  fun freezeDataContext(dataContext: DataContext, missedKeys: Consumer<in String?>?): DataContext {
    return if (dataContext is PreCachedDataContext) dataContext.frozenCopy(missedKeys) else dataContext
  }

  @JvmStatic
  fun isAsyncDataContext(dataContext: DataContext): Boolean = dataContext === DataContext.EMPTY_CONTEXT || dataContext is AsyncDataContext

  @JvmStatic
  fun getRawDataIfCached(dataContext: DataContext, dataId: String): Any? = when (dataContext) {
    is PreCachedDataContext -> dataContext.getRawDataIfCached(dataId)
    is EdtDataContext -> dataContext.getRawDataIfCached(dataId)
    else -> null
  }

  @JvmStatic
  fun clearAllCachesAndUpdates() {
    cancelAllUpdates("clear-all-caches-and-updates requested")
    waitForAllUpdatesToFinish()
    PreCachedDataContext.clearAllCaches()
  }

  @JvmStatic
  fun expandActionGroupAsync(group: ActionGroup,
                             presentationFactory: PresentationFactory,
                             context: DataContext,
                             place: String): CancellablePromise<List<AnAction>> {
    return expandActionGroupAsync(group = group,
                                  presentationFactory = presentationFactory,
                                  context = context,
                                  place = place,
                                  isToolbarAction = false,
                                  fastTrack = true)
  }

  @JvmStatic
  fun expandActionGroupAsync(group: ActionGroup,
                             presentationFactory: PresentationFactory,
                             context: DataContext,
                             place: String,
                             isToolbarAction: Boolean,
                             fastTrack: Boolean): CancellablePromise<List<AnAction>> {
    val async = isAsyncDataContext(context)
    if (!async) {
      throw AssertionError("Async data context required in '$place': ${dumpDataContextClass(context)}")
    }
    val edtExecutor = if (fastTrack) {
      newFastTrackAwareExecutor(group = group, place = place, context = context, checkMainMenuOrToolbarFirstTime = true)
    }
    else {
      null
    }
    val updater = ActionUpdater(presentationFactory = presentationFactory,
                                dataContext = context,
                                place = place,
                                contextMenuAction = ActionPlaces.isPopupPlace(place),
                                toolbarAction = isToolbarAction,
                                edtExecutor = edtExecutor,
                                eventTransform = null)
    val promise = updater.expandActionGroupAsync(group = group, hideDisabled = group is CompactActionGroup)
    edtExecutor?.waitForFastTrack(promise)
    return promise
  }

  @JvmStatic
  fun expandActionGroupWithTimeout(group: ActionGroup,
                                   presentationFactory: PresentationFactory,
                                   context: DataContext,
                                   place: String,
                                   isToolbarAction: Boolean,
                                   timeoutMs: Int): List<AnAction> {
    return ActionUpdater(presentationFactory, context, place, ActionPlaces.isPopupPlace(place), isToolbarAction)
      .expandActionGroupWithTimeout(group, group is CompactActionGroup, timeoutMs)
  }

  @JvmStatic
  fun expandActionGroup(group: ActionGroup,
                        presentationFactory: PresentationFactory,
                        context: DataContext,
                        place: String): List<AnAction> {
    val point = if (PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(context) == null) null
    else JBPopupFactory.getInstance().guessBestPopupLocation(context)
    val removeIcon = addLoadingIcon(point, place)
    var result: List<AnAction>? = null
    val span = getTracer(false).spanBuilder("expandActionGroup").setAttribute("place", place).startSpan()
    val start = System.nanoTime()
    try {
      result = Context.current().with(span).with(OT_ENABLE_SPANS, true).makeCurrent().use {
        computeWithRetries(null, removeIcon) {
          expandActionGroupImpl(group, presentationFactory, context, place, ActionPlaces.isPopupPlace(place), removeIcon, null)
        }
      }
      return result
    }
    finally {
      val elapsed = TimeoutUtil.getDurationMillis(start)
      span.end()
      if (elapsed > 1000) {
        LOG.warn("$elapsed ms to expandActionGroup@$place")
      }
      recordActionGroupExpanded(group, context, place, false, elapsed, result)
    }
  }

  private var ourExpandActionGroupImplEDTLoopLevel = 0

  private fun expandActionGroupImpl(group: ActionGroup,
                                    presentationFactory: PresentationFactory,
                                    context: DataContext,
                                    place: String,
                                    isContextMenu: Boolean,
                                    onProcessed: (() -> Unit)?,
                                    menuItem: Component?): List<AnAction> {
    val isUnitTestMode = ApplicationManager.getApplication().isUnitTestMode()
    val wrapped = wrapDataContext(context)
    val async = isAsyncDataContext(wrapped) && !isUnitTestMode
    val edtExecutor = if (async) newFastTrackAwareExecutor(group, place, context, false) else null
    val updater = ActionUpdater(presentationFactory = presentationFactory,
                                dataContext = wrapped,
                                place = place,
                                contextMenuAction = isContextMenu,
                                toolbarAction = false,
                                edtExecutor = edtExecutor,
                                eventTransform = null)
    var list: List<AnAction>?
    if (async) {
      if (isContextMenu) {
        cancelAllUpdates("context menu requested")
      }
      val maxLoops = max(2.0, Registry.intValue("actionSystem.update.actions.async.max.nested.loops", 20).toDouble()).toInt()
      if (ourExpandActionGroupImplEDTLoopLevel >= maxLoops) {
        LOG.warn("Maximum number of recursive EDT loops reached ($maxLoops) at '$place'")
        onProcessed?.invoke()
        cancelAllUpdates("recursive EDT loops limit reached at '$place'")
        throw ProcessCanceledException()
      }
      val promise = updater.expandActionGroupAsync(group, group is CompactActionGroup)
      if (edtExecutor != null) {
        list = edtExecutor.waitForFastTrack(promise)
        if (list != null) {
          onProcessed?.invoke()
          return list
        }
      }
      if (onProcessed != null) {
        promise.onSuccess { onProcessed.invoke() }
        promise.onError { ex -> if (!canRetryOnThisException(ex)) onProcessed.invoke() }
      }
      val queue = IdeEventQueue.getInstance()
      val contextComponent = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(wrapped)
      try {
        resetThreadContext().use {
          cancelOnUserActivityInside(promise, contextComponent, menuItem).use {
            ourExpandActionGroupImplEDTLoopLevel++
            list = runLoopAndWaitForFuture(promise, emptyList(), true) {
              val event = queue.getNextEvent()
              queue.dispatchEvent(event)
              true
            }
          }
        }
      }
      finally {
        ourExpandActionGroupImplEDTLoopLevel--
      }
      if (promise.isCancelled) {
        // to avoid duplicate "Nothing Here" items in menu bar
        // and "Nothing Here"-only popup menus
        throw ProcessCanceledException()
      }
    }
    else {
      if (Registry.`is`("actionSystem.update.actions.async") && !isUnitTestMode) {
        LOG.error("Async data context required in '" + place + "': " + dumpDataContextClass(wrapped))
      }
      try {
        list = if (DO_FULL_EXPAND) updater.expandActionGroupFull(group, group is CompactActionGroup)
        else updater.expandActionGroupWithTimeout(group, group is CompactActionGroup)
      }
      finally {
        onProcessed?.invoke()
      }
    }
    return list!!
  }

  private fun cancelOnUserActivityInside(promise: CancellablePromise<List<AnAction>>,
                                         contextComponent: Component?,
                                         menuItem: Component?): AccessToken {
    val window: Window? = if (contextComponent == null) null else SwingUtilities.getWindowAncestor(contextComponent)
    return ProhibitAWTEvents.startFiltered("expandActionGroup") { event ->
      if (event is FocusEvent && event.getID() == FocusEvent.FOCUS_LOST && event.cause == FocusEvent.Cause.ACTIVATION &&
          window != null && window === SwingUtilities.getWindowAncestor(event.component) ||
          event is KeyEvent && event.getID() == KeyEvent.KEY_PRESSED ||
          event is MouseEvent && event.getID() == MouseEvent.MOUSE_PRESSED &&
          UIUtil.getDeepestComponentAt(event.component, event.x, event.y) !== menuItem)
        cancelPromise(promise, event)
      null
    }
  }

  private fun newFastTrackAwareExecutor(group: ActionGroup,
                                        place: String,
                                        context: DataContext,
                                        checkMainMenuOrToolbarFirstTime: Boolean): FastTrackAwareEdtExecutor? {
    if (!ActionGroupExpander.getInstance().allowsFastUpdate(CommonDataKeys.PROJECT.getData(context), group, place)) {
      return null
    }


    val mainMenuOrToolbarFirstTime = checkMainMenuOrToolbarFirstTime &&
                                     (ActionPlaces.MAIN_MENU == place || (ExperimentalUI.isNewUI() && ActionPlaces.MAIN_TOOLBAR == place))
    val maxTime = if (mainMenuOrToolbarFirstTime) 5000 else Registry.intValue("actionSystem.update.actions.async.fast-track.timeout.ms", 50)
    return if (maxTime < 1) null else FastTrackAwareEdtExecutor(maxTime)
  }

  fun fillPopUpMenu(group: ActionGroup,
                    component: JComponent,
                    presentationFactory: PresentationFactory,
                    context: DataContext,
                    place: String,
                    progressPoint: RelativePoint?) {
    fillMenu(group = group,
             component = component,
             enableMnemonics = !UISettings.getInstance().disableMnemonics,
             presentationFactory = presentationFactory,
             context = context,
             place = place,
             isWindowMenu = false,
             useDarkIcons = false,
             nativePeer = null,
             progressPoint = progressPoint,
             expire = null)
  }

  internal fun fillMenu(group: ActionGroup,
                        component: Component,
                        nativePeer: Menu?,
                        enableMnemonics: Boolean,
                        presentationFactory: PresentationFactory,
                        context: DataContext,
                        place: String,
                        isWindowMenu: Boolean,
                        useDarkIcons: Boolean,
                        progressPoint: RelativePoint? = null,
                        expire: (() -> Boolean)?) {
    if (ApplicationManagerEx.getApplicationEx().isWriteActionInProgress()) {
      throw ProcessCanceledException()
    }
    if (Thread.holdsLock(component.treeLock)) {
      throw ProcessCanceledException()
    }
    var result: List<AnAction>? = null
    val span = getTracer(checkNoop = false).spanBuilder("fillMenu").setAttribute("place", place).startSpan()
    val start = System.nanoTime()
    try {
      Context.current().with(span).with(OT_ENABLE_SPANS, true).makeCurrent().use {
        val removeIcon = if (isWindowMenu) null else addLoadingIcon(progressPoint, place)
        val list = computeWithRetries(expire, removeIcon) {
          expandActionGroupImpl(group = group,
                                presentationFactory = presentationFactory,
                                context = context,
                                place = place,
                                isContextMenu = true,
                                onProcessed = removeIcon,
                                menuItem = component)
        }
        result = list
        val checked = group is CheckedActionGroup
        val multiChoice = isMultiChoiceGroup(group)
        if (nativePeer == null) {
          fillMenuInner(component = component as JComponent,
                        list = list,
                        checked = checked,
                        multiChoice = multiChoice,
                        enableMnemonics = enableMnemonics,
                        presentationFactory = presentationFactory,
                        context = context,
                        place = place,
                        isWindowMenu = isWindowMenu,
                        useDarkIcons = useDarkIcons)
        }
        else {
          fillMenuInnerMacNative(nativePeer = nativePeer,
                                 list = list,
                                 checked = checked,
                                 multiChoice = multiChoice,
                                 enableMnemonics = enableMnemonics,
                                 presentationFactory = presentationFactory,
                                 context = context,
                                 place = place,
                                 frame = component as JFrame,
                                 useDarkIcons = useDarkIcons)
        }
      }
    }
    finally {
      val elapsed = TimeoutUtil.getDurationMillis(start)
      span.end()
      if (elapsed > 1000) {
        LOG.warn("$elapsed ms to fillMenu@$place")
      }
      val submenu = component is ActionMenu && component.getParent() != null
      recordActionGroupExpanded(action = group, context = context, place = place, submenu = submenu, durationMs = elapsed, result = result)
    }
  }

  private fun addLoadingIcon(point: RelativePoint?, place: String): (() -> Unit)? {
    if (!Registry.`is`("actionSystem.update.actions.async", true)) {
      return null
    }

    val rootPane = if (point == null) null else UIUtil.getRootPane(point.component)
    val glassPane = (if (rootPane == null) null else rootPane.glassPane as JComponent?) ?: return null
    val comp = point!!.originalComponent
    if ((comp is ActionMenu && comp.getParent() is IdeJMenuBar) ||
        (ActionPlaces.EDITOR_GUTTER_POPUP == place &&
         comp is EditorGutterComponentEx &&
         comp.getGutterRenderer(point.originalPoint) != null)) {
      return null
    }

    val isMenuItem = comp is ActionMenu
    val icon = JLabel(if (isMenuItem) AnimatedIcon.Default.INSTANCE else AnimatedIcon.Big.INSTANCE)
    val size = icon.getPreferredSize()
    icon.size = size
    val location = point.getPoint(glassPane)
    if (isMenuItem) {
      location.x -= 2 * size.width
      location.y += (comp.size.height - size.height + 1) / 2
    }
    else {
      location.x -= size.width / 2
      location.y -= size.height / 2
    }
    icon.location = location
    EdtScheduledExecutorService.getInstance().schedule(Runnable {
      if (icon.isVisible) {
        glassPane.add(icon)
      }
    }, Registry.intValue("actionSystem.popup.progress.icon.delay", 500).toLong(), TimeUnit.MILLISECONDS)
    return {
      if (icon.parent != null) glassPane.remove(icon)
      else icon.isVisible = false
    }
  }

  private fun fillMenuInner(component: JComponent,
                            list: List<AnAction>,
                            checked: Boolean,
                            multiChoice: Boolean,
                            enableMnemonics: Boolean,
                            presentationFactory: PresentationFactory,
                            context: DataContext,
                            place: String,
                            isWindowMenu: Boolean,
                            useDarkIcons: Boolean) {
    component.removeAll()
    val filtered = filterInvisible(list, presentationFactory, place)
    val children = ArrayList<Component>()
    for (action in filtered) {
      val presentation = presentationFactory.getPresentation(action)
      if (multiChoice && action is Toggleable) {
        presentation.isMultiChoice = true
      }
      var childComponent: JComponent
      if (action is Separator) {
        childComponent = createSeparator(action.text, children.isEmpty())
      }
      else if (action is ActionGroup && !isSubmenuSuppressed(presentation)) {
        val menu = ActionMenu(context = context,
                              place = place,
                              group = action,
                              presentationFactory = presentationFactory,
                              isMnemonicEnabled = enableMnemonics,
                              useDarkIcons = useDarkIcons)
        childComponent = menu
      }
      else {
        val each = ActionMenuItem(action, place, context, enableMnemonics, checked, useDarkIcons)
        each.updateFromPresentation(presentation)
        childComponent = each
      }
      component.add(childComponent)
      children.add(childComponent)
    }
    if (list.isEmpty()) {
      val presentation = presentationFactory.getPresentation(EMPTY_MENU_FILLER)
      val each = ActionMenuItem(EMPTY_MENU_FILLER, place, context, enableMnemonics, checked, useDarkIcons)
      each.updateFromPresentation(presentation)
      component.add(each)
      children.add(each)
    }
    if (SystemInfo.isMacSystemMenu && isWindowMenu) {
      if (isAligned) {
        val icon = if (hasIcons(children)) EMPTY_MENU_ACTION_ICON else null
        children.forEach { child -> replaceIconIn(child, icon) }
      }
      else if (isAlignedInGroup) {
        val currentGroup = ArrayList<Component>()
        for (i in children.indices) {
          val child = children[i]
          val isSeparator = child is JPopupMenu.Separator
          val isLastElement = i == children.size - 1
          if (isLastElement || isSeparator) {
            if (isLastElement && !isSeparator) {
              currentGroup.add(child)
            }
            val icon = if (hasIcons(currentGroup)) EMPTY_MENU_ACTION_ICON else null
            currentGroup.forEach { menuItem -> replaceIconIn(menuItem, icon) }
            currentGroup.clear()
          }
          else {
            currentGroup.add(child)
          }
        }
      }
    }
  }

  private fun fillMenuInnerMacNative(nativePeer: Menu,
                                     frame: JFrame,
                                     list: List<AnAction>,
                                     checked: Boolean,
                                     multiChoice: Boolean,
                                     enableMnemonics: Boolean,
                                     presentationFactory: PresentationFactory,
                                     context: DataContext,
                                     place: String,
                                     useDarkIcons: Boolean) {
    val filtered = filterInvisible(list = list, presentationFactory = presentationFactory, place = place)
    for (action in filtered) {
      val presentation = presentationFactory.getPresentation(action)
      if (multiChoice && action is Toggleable) {
        presentation.isMultiChoice = true
      }
      var peer: MenuItem?
      if (action is Separator) {
        peer = null
      }
      else if (action is ActionGroup && !isSubmenuSuppressed(presentation)) {
        peer = createMacNativeActionMenu(context = context,
                                         place = place,
                                         group = action,
                                         presentationFactory = presentationFactory,
                                         isMnemonicEnabled = enableMnemonics,
                                         frame = frame,
                                         useDarkIcons = useDarkIcons)
      }
      else {
        peer = MacNativeActionMenuItem(action = action,
                                       place = place,
                                       context = context,
                                       isMnemonicEnabled = enableMnemonics,
                                       insideCheckedGroup = checked,
                                       useDarkIcons = useDarkIcons,
                                       presentation = presentation).menuItemPeer
      }
      // null peer means `null`
      nativePeer.add(peer)
    }
  }

  private fun filterInvisible(list: List<AnAction>,
                              presentationFactory: PresentationFactory,
                              place: String): List<AnAction> {
    val filtered = ArrayList<AnAction>(list.size)
    for (action in list) {
      val presentation = presentationFactory.getPresentation(action)
      if (!presentation.isVisible) {
        reportInvisibleMenuItem(action, place)
        continue
      }
      if (action !is Separator && presentation.text.isNullOrEmpty()) {
        reportEmptyTextMenuItem(action, place)
        continue
      }
      if (action is Separator) {
        val lastIdx = filtered.size - 1
        if (lastIdx < 0 && action.text.isNullOrEmpty()) {
          continue
        }
        if (lastIdx >= 0 && filtered[lastIdx] is Separator) {
          filtered[lastIdx] = action
          continue
        }
      }
      filtered.add(action)
    }
    val lastIdx = filtered.size - 1
    if (lastIdx >= 0 && filtered[lastIdx].let { it is Separator && it.text.isNullOrEmpty() }) {
      filtered.removeAt(lastIdx)
    }
    return filtered
  }

  private fun reportInvisibleMenuItem(action: AnAction, place: String) {
    val operationName = operationName(action = action, op = null, place = place)
    LOG.error("Invisible menu item for $operationName")
  }

  private fun reportEmptyTextMenuItem(action: AnAction, place: String) {
    val operationName = operationName(action, null, place)
    var message = "Empty menu item text for $operationName"
    if (action.getTemplatePresentation().text.isNullOrEmpty()) {
      message += ". The default action text must be specified in plugin.xml or its class constructor"
    }
    LOG.error(PluginException.createByClass(message, null, action.javaClass))
  }

  @JvmStatic
  fun operationName(action: Any, op: String?, place: String?): String {
    var c: Class<*> = action.javaClass
    val sb = StringBuilder(200)
    if (!op.isNullOrEmpty()) {
      sb.append('#').append(op)
    }
    if (!place.isNullOrEmpty()) {
      sb.append('@').append(place)
    }
    sb.append(" (")
    var x = action
    while (x is ActionWithDelegate<*>) {
      sb.append(StringUtilRt.getShortName(c.getName())).append('/')
      x = x.getDelegate()
      c = x.javaClass
    }
    sb.append(c.getName()).append(")")
    sb.insert(0, StringUtilRt.getShortName(c.getName()))
    return sb.toString()
  }

  private fun dumpDataContextClass(context: DataContext): String {
    var c = context.javaClass
    val sb = StringBuilder(200)
    var i = 0
    var x: Any = context
    while (x is CustomizedDataContext) {
      sb.append(StringUtilRt.getShortName(c.getName())).append('(')
      x = x.getParent()
      i++
      c = x.javaClass
    }
    sb.append(c.getName())
    repeat(i) {
      sb.append(')')
    }
    return sb.toString()
  }

  @JvmStatic
  fun isMultiChoiceGroup(actionGroup: ActionGroup): Boolean {
    val p = actionGroup.getTemplatePresentation()
    if (p.isMultiChoice) return true
    if (p.icon === AllIcons.Actions.GroupBy || p.icon === AllIcons.Actions.Show || p.icon === AllIcons.General.GearPlain || p.icon === AllIcons.Debugger.RestoreLayout) {
      return true
    }
    if (actionGroup.javaClass == DefaultActionGroup::class.java) {
      for (child in actionGroup.getChildren(null)) {
        if (child is Separator) continue
        if (child !is Toggleable) return false
      }
      return true
    }
    return false
  }

  @JvmStatic
  fun updateMenuItems(popupMenu: JPopupMenu,
                      dataContext: DataContext,
                      place: String,
                      presentationFactory: PresentationFactory) {
    val items = popupMenu.components.filterIsInstance<ActionMenuItem>()
    updateComponentActions(component = popupMenu,
                           actions = items.map { it.anAction },
                           dataContext = dataContext,
                           place = place,
                           presentationFactory = presentationFactory, onUpdate = {
      for (item in items) {
        item.updateFromPresentation(presentationFactory.getPresentation(item.anAction))
      }
    })
  }

  @JvmStatic
  fun updateComponentActions(component: JComponent,
                             actions: Iterable<AnAction>,
                             dataContext: DataContext,
                             place: String,
                             presentationFactory: PresentationFactory,
                             onUpdate: Runnable) {
    val actionGroup = DefaultActionGroup()
    for (action in actions) {
      actionGroup.add(action)
    }
    // note that no retries are attempted
    if (isAsyncDataContext(dataContext)) {
      expandActionGroupAsync(actionGroup, presentationFactory, dataContext, place)
        .onSuccess {
          try {
            onUpdate.run()
          }
          finally {
            component.repaint()
          }
        }
    }
    else {
      expandActionGroupImpl(group = actionGroup,
                            presentationFactory = presentationFactory,
                            context = dataContext,
                            place = place,
                            isContextMenu = ActionPlaces.isPopupPlace(place),
                            onProcessed = null,
                            menuItem = null)
      onUpdate.run()
    }
  }

  @JvmStatic
  fun isSubmenuSuppressed(presentation: Presentation): Boolean {
    return presentation.getClientProperty(SUPPRESS_SUBMENU_IMPL) == true
  }

  private fun createSeparator(text: @NlsContexts.Separator String?, first: Boolean): JPopupMenu.Separator {
    return object : JPopupMenu.Separator() {
      private val menu: GroupHeaderSeparator?

      init {
        if (!text.isNullOrEmpty()) {
          val labelInsets = if (ExperimentalUI.isNewUI()) JBUI.CurrentTheme.Popup.separatorLabelInsets() else JBUI.CurrentTheme.ActionsList.cellPadding()
          menu = GroupHeaderSeparator(labelInsets)
          menu.caption = text
          menu.setHideLine(first)
        }
        else {
          menu = null
        }
      }

      override fun doLayout() {
        super.doLayout()
        menu?.bounds = bounds
      }

      override fun paintComponent(g: Graphics) {
        if (isUnderDarcula || StartupUiUtil.isUnderWin10LookAndFeel()) {
          g.color = parent.getBackground()
          g.fillRect(0, 0, width, height)
        }
        if (menu != null) {
          menu.paint(g)
        }
        else {
          super.paintComponent(g)
        }
      }

      override fun getPreferredSize(): Dimension = if (menu == null) super.getPreferredSize() else menu.preferredSize
    }
  }

  private fun replaceIconIn(menuItem: Component, icon: Icon?) {
    val from = if (icon == null) EMPTY_MENU_ACTION_ICON else null
    if (menuItem is ActionMenuItem && menuItem.icon === from) {
      menuItem.setIcon(icon)
    }
    else if (menuItem is ActionMenu && menuItem.icon === from) {
      menuItem.setIcon(icon)
    }
  }

  private fun hasIcons(components: List<Component>): Boolean = components.find { hasNotEmptyIcon(it) } != null

  private fun hasNotEmptyIcon(comp: Component): Boolean {
    val icon: Icon? = when (comp) {
      is ActionMenuItem -> comp.icon
      is ActionMenu -> comp.icon
      else -> null
    }
    return icon != null && icon !== EMPTY_MENU_ACTION_ICON
  }

  /**
   * Check if the `component` represents a modal context in a general sense,
   * i.e., whether any of its parents is either a modal [Window]
   * or explicitly marked to be treated like a modal context.
   * @see Utils.markAsModalContext
   */
  @JvmStatic
  fun isModalContext(component: Component): Boolean {
    val implicitValue = IdeKeyEventDispatcher.isModalContextOrNull(component)
    if (implicitValue != null) {
      return implicitValue
    }
    var cur: Component? = component
    do {
      val explicitValue = ClientProperty.get(cur, IS_MODAL_CONTEXT)
      if (explicitValue != null) {
        return explicitValue
      }
      cur = cur?.parent
    }
    while (cur != null)
    return true
  }

  /**
   * Mark the `component` to be treated like a modal context (or not) when it cannot be deduced implicitly from UI hierarchy.
   * @param isModalContext `null` to clear a mark, to set a new one otherwise.
   * @see Utils.isModalContext
   */
  fun markAsModalContext(component: JComponent, isModalContext: Boolean?) {
    ClientProperty.put(component, IS_MODAL_CONTEXT, isModalContext)
  }

  @Deprecated("Use {@link AnActionEvent#getUpdateSession()}")
  @JvmStatic
  fun getOrCreateUpdateSession(e: AnActionEvent): UpdateSession {
    initUpdateSession(e)
    return e.updateSession
  }

  @JvmStatic
  fun initUpdateSession(e: AnActionEvent) {
    var updater = e.updateSession
    if (updater === UpdateSession.EMPTY) {
      @Suppress("removal", "DEPRECATION")
      val actionUpdater = ActionUpdater(presentationFactory = PresentationFactory(),
                                        dataContext = e.dataContext,
                                        place = e.place,
                                        contextMenuAction = e.isFromContextMenu,
                                        toolbarAction = e.isFromActionToolbar)
      updater = actionUpdater.asUpdateSession()
      e.updateSession = updater
    }
  }

  private var ourInUpdateSessionForInputEventEDTLoop = false

  fun <T> runUpdateSessionForInputEvent(actions: List<AnAction>,
                                        inputEvent: InputEvent,
                                        dataContext: DataContext,
                                        place: String,
                                        actionProcessor: ActionProcessor,
                                        factory: PresentationFactory,
                                        eventTracker: ((AnActionEvent) -> Unit)?,
                                        function: (UpdateSession, List<AnAction>) -> T): T? {
    val applicationEx = ApplicationManagerEx.getApplicationEx()
    if (ProgressIndicatorUtils.isWriteActionRunningOrPending(applicationEx)) {
      LOG.error("Actions cannot be updated when write-action is running or pending")
      return null
    }
    if (ourInUpdateSessionForInputEventEDTLoop) {
      LOG.warn("Recursive shortcut processing invocation is ignored")
      return null
    }
    val start = System.nanoTime()
    val async = isAsyncDataContext(dataContext)
    // we will manually process "invokeLater" calls using a queue for performance reasons:
    // direct approach would be to pump events in a custom modality state (enterModal/leaveModal)
    // EventQueue would add significant overhead (x10), but key events must be processed ASAP.
    val queue: BlockingQueue<Runnable>? = if (async) LinkedBlockingQueue() else null
    val actionUpdater = ActionUpdater(
      presentationFactory = factory,
      dataContext = dataContext,
      place = place,
      contextMenuAction = false,
      toolbarAction = false,
      edtExecutor = if (async) Executor { e -> queue!!.offer(e) } else null,
      eventTransform = { event ->
        val transformed = actionProcessor.createEvent(inputEvent, event.dataContext, event.place, event.presentation, event.actionManager)
        eventTracker?.invoke(transformed)
        transformed
      },
    )
    val result: T?
    if (async) {
      queue!!
      cancelAllUpdates("'$place' invoked")
      val promise = newPromise<T>(place)
      val parentIndicator = ProgressIndicatorProvider.getGlobalProgressIndicator()
      beforePerformedExecutor.execute {
        try {
          var ref: T? = null
          val computable = ThrowableComputable<Void?, RuntimeException> {
            val adjusted = ArrayList(actions)
            actionUpdater.tryRunReadActionAndCancelBeforeWrite(promise) {
              rearrangeByPromoters(adjusted, dataContext)
            }
            if (promise.isDone()) {
              return@ThrowableComputable null
            }
            val session = actionUpdater.asUpdateSession()
            actionUpdater.tryRunReadActionAndCancelBeforeWrite(promise) {
              ref = function(session, adjusted)
            }
            queue.offer { actionUpdater.applyPresentationChanges() }
            null
          }
          val indicator: ProgressIndicator = if (parentIndicator == null) ProgressIndicatorBase()
          else SensitiveProgressWrapper(parentIndicator)
          promise.onError { indicator.cancel() }
          ProgressManager.getInstance().executeProcessUnderProgress(
            { ProgressManager.getInstance().computePrioritized(computable) },
            indicator)
          queue.offer { promise.setResult(ref) }
        }
        catch (e: Throwable) {
          promise.setError(e)
        }
      }
      try {
        ourInUpdateSessionForInputEventEDTLoop = true
        result = runLoopAndWaitForFuture(promise = promise, defValue = null, rethrowCancellation = false) {
          val runnable = queue.poll(1, TimeUnit.MILLISECONDS)
          runnable?.run()
          parentIndicator?.checkCanceled()
          true
        }
      }
      finally {
        ourInUpdateSessionForInputEventEDTLoop = false
      }
    }
    else {
      val adjusted = ArrayList(actions)
      rearrangeByPromoters(adjusted, dataContext)
      result = function(actionUpdater.asUpdateSession(), adjusted)
      actionUpdater.applyPresentationChanges()
    }
    val elapsed = TimeoutUtil.getDurationMillis(start)
    if (elapsed > 1000) {
      LOG.warn("$elapsed ms to runUpdateSessionForInputEvent@$place")
    }
    return result
  }

  fun rearrangeByPromoters(actions: MutableList<AnAction>, dataContext: DataContext) {
    val frozenContext = freezeDataContext(dataContext = dataContext, missedKeys = null)
    val readOnlyActions = Collections.unmodifiableList(actions)
    val promoters = ActionPromoter.EP_NAME.extensionList + actions.filterIsInstance<ActionPromoter>()
    for (promoter in promoters) {
      try {
        SlowOperations.startSection(SlowOperations.FORCE_ASSERT).use {
          val promoted = promoter.promote(readOnlyActions, frozenContext)
          if (!promoted.isNullOrEmpty()) {
            actions.removeAll(promoted)
            actions.addAll(0, promoted)
          }
          val suppressed = promoter.suppress(readOnlyActions, frozenContext)
          if (!suppressed.isNullOrEmpty()) {
            actions.removeAll(suppressed)
          }
        }
      }
      catch (ignore: ProcessCanceledException) {
      }
      catch (e: Throwable) {
        LOG.error(e)
      }
    }
  }
}

private fun <T> runLoopAndWaitForFuture(promise: Future<out T?>,
                                        defValue: T?,
                                        rethrowCancellation: Boolean,
                                        eventPump: () -> Boolean): T? {
  while (!promise.isDone) {
    try {
      if (!eventPump()) {
        return null
      }
    }
    catch (ignore: ProcessCanceledException) {
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }
  try {
    return if (promise.isCancelled) defValue else promise.get()
  }
  catch (ex: Throwable) {
    val pce = ExceptionUtilRt.findCause(ex, ProcessCanceledException::class.java)
    if (pce == null) {
      LOG.error(ex)
    }
    else if (rethrowCancellation) {
      throw pce
    }
  }
  return defValue
}

@RequiresEdt
private fun <T> computeWithRetries(expire: (() -> Boolean)?, onProcessed: (() -> Unit)?, computable: () -> T): T {
  var lastCancellation: ProcessCanceledWithReasonException? = null
  val retries = max(1.0, Registry.intValue("actionSystem.update.actions.max.retries", 20).toDouble()).toInt()
  for (i in 0 until retries) {
    try {
      SlowOperations.startSection(SlowOperations.RESET).use {
        return computable()
      }
    }
    catch (ex: ProcessCanceledWithReasonException) {
      lastCancellation = ex
      if (canRetryOnThisException(ex) && (expire == null || !expire())) {
        continue
      }
      throw ex
    }
    catch (e: Throwable) {
      throw e
    }
    finally {
      onProcessed?.invoke()
    }
  }
  if (retries > 1) {
    LOG.warn("Maximum number of retries to show a menu reached (" + retries + "): " + lastCancellation!!.reason)
  }
  throw lastCancellation!!
}

private fun canRetryOnThisException(ex: Throwable): Boolean {
  val reason = if (ex is ProcessCanceledWithReasonException) ex.reason else null
  val reasonStr = if (reason is String) reason else ""
  return reasonStr.contains("write-action") || reasonStr.contains("fast-track") && reasonStr.contains("toolbar", ignoreCase = true)
}

private class FastTrackAwareEdtExecutor(val maxTime: Int) : Executor {
  private val queue = LinkedBlockingQueue<Runnable>()

  @Volatile
  private var fastTrack: Boolean = true

  override fun execute(runnable: Runnable) {
    if (fastTrack) {
      queue.offer(runnable)
    }
    else {
      ApplicationManager.getApplication().invokeLater(runnable, ModalityState.any())
    }
  }

  fun <T> waitForFastTrack(promise: Future<out T>): T? {
    val result: T?
    val start = System.nanoTime()
    try {
      fastTrack = true
      result = runLoopAndWaitForFuture(promise = promise, defValue = null, rethrowCancellation = false) {
        val runnable = queue.poll(1, TimeUnit.MILLISECONDS)
        runnable?.run()
        TimeoutUtil.getDurationMillis(start) < maxTime
      }
    }
    finally {
      fastTrack = false
    }
    if (result == null) {
      for (runnable in queue) {
        ApplicationManager.getApplication().invokeLater(runnable, ModalityState.any())
      }
      queue.clear()
    }
    LOG.assertTrue(queue.isEmpty(), "fast-track queue is not empty")
    return result
  }
}

internal class ProcessCanceledWithReasonException(val reason: Any) : ProcessCanceledException() {
  override fun fillInStackTrace(): Throwable = this
}