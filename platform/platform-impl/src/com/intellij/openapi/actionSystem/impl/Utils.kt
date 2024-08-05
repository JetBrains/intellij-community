// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(IntellijInternalApi::class, ExperimentalStdlibApi::class)

package com.intellij.openapi.actionSystem.impl

import com.intellij.CommonBundle
import com.intellij.codeWithMe.ClientId
import com.intellij.concurrency.ContextAwareRunnable
import com.intellij.concurrency.IntelliJContextElement
import com.intellij.concurrency.resetThreadContext
import com.intellij.diagnostic.PluginException
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.ui.UISettings
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionIdProvider
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl.Companion.recordActionGroupExpanded
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionMenu.Companion.isAligned
import com.intellij.openapi.actionSystem.impl.ActionMenu.Companion.isAlignedInGroup
import com.intellij.openapi.actionSystem.util.ActionSystem
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.keymap.impl.ActionProcessor
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.impl.ProgressManagerImpl
import com.intellij.openapi.progress.util.PotemkinOverlayProgress
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.ide.menu.IdeJMenuBar
import com.intellij.platform.ide.menu.MacNativeActionMenuItem
import com.intellij.platform.ide.menu.createMacNativeActionMenu
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ClientProperty
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.GroupHeaderSeparator
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.mac.screenmenu.Menu
import com.intellij.util.SlowOperations
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.*
import com.intellij.util.ui.update.UiNotifyConnector
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.concurrency.CancellablePromise
import org.jetbrains.concurrency.asCancellablePromise
import java.awt.*
import java.awt.event.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import javax.swing.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.max

internal val EMPTY_MENU_ACTION_ICON: Icon = EmptyIcon.create(16, 1)

private val IS_MODAL_CONTEXT = Key.create<Boolean>("Component.isModalContext")
private val OT_ENABLE_SPANS: ContextKey<Boolean> = ContextKey.named("OT_ENABLE_SPANS")
private var ourExpandActionGroupImplEDTLoopLevel = 0
private var ourInUpdateSessionForInputEventEDTLoop = false

private val LOG = logger<Utils>()

// Any menu expansion can freeze and block precious threads, so process
// 1. Shortcuts ASAP, and employ unbounded Dispatchers.IO
// 2. Menus the same way using different dispatcher
// 3. Fast-track toolbars in a limited dispatcher, and limit subsequent fast-tracks
// 4. Regular toolbars in a limited dispatcher
internal val fastParallelism = (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(2)
@OptIn(ExperimentalCoroutinesApi::class)
private val shortcutUpdateDispatcher = Dispatchers.IO.limitedParallelism(fastParallelism)
@OptIn(ExperimentalCoroutinesApi::class)
private val contextMenuDispatcher = Dispatchers.IO.limitedParallelism(fastParallelism)
@OptIn(ExperimentalCoroutinesApi::class)
private val toolbarFastDispatcher = Dispatchers.IO.limitedParallelism(2)
@OptIn(ExperimentalCoroutinesApi::class)
private val toolbarDispatcher = Dispatchers.Default.limitedParallelism(2)

// Stacking fast-tracks UI freeze protection
private var lastFailedFastTrackFinishNanos = 0L
private var lastFailedFastTrackCount = 0

/**
 * The main utility to expand action groups asynchronously (without blocking EDT).
 *
 * Action update session is always run in BGT.
 *
 * There are three ways to deal with EDT during that:
 *
 * 1. Suspending approach via [Utils.expandActionGroupSuspend].
 *    The call is non-blocking but can optionally block EDT for a short period of time (50 ms).
 *    It is used by **toolbars**, and they often expand on [JComponent.addNotify].
 *    If their action groups are fast to expand, then the UI will appear instantly.
 *    Otherwise, a progress icon is shown while expecting the results.
 *
 * 2. Blocking without blocking the UI approach via [Utils.expandActionGroup].
 *    The call blocks the caller but keeps the UI running using secondary EDT loop inside.
 *    It is used by **menus**, **submenus**, and **popups**, because Swing menus expect synchronous code.
 *    A progress icon is shown while expecting the results.
 *
 * 3. Blocking with the ability to unblock via [Utils.runUpdateSessionForInputEvent].
 *    It is only used in **keyboard, mouse, and gesture shortcuts** processing.
 *    It fully blocks the UI while it chooses the action to invoke because the user expects actions
 *    to perform on the exact UI state at the time shortcut is pressed.
 *    In case of a long wait, [PotemkinOverlayProgress] is shown with the ability to cancel the shortcut.
 */
@ApiStatus.Internal
object Utils {
  @JvmField
  val EMPTY_MENU_FILLER: AnAction = EmptyAction().apply {
    getTemplatePresentation().setText(CommonBundle.messagePointer("empty.menu.filler"))
  }

  internal fun getTracer(checkNoop: Boolean): Tracer {
    return if (checkNoop && Context.current().get(OT_ENABLE_SPANS) != true) {
      OpenTelemetry.noop().getTracer("")
    }
    else {
      TelemetryManager.getInstance().getTracer(ActionSystem)
    }
  }

  @JvmStatic
  fun createAsyncDataContext(dataContext: DataContext): DataContext {
    val result = createAsyncDataContextImpl(dataContext)
    if ((result as? PreCachedDataContext)?.cachesAllKnownDataKeys() == false) {
      return createAsyncDataContextImpl(dataContext) // recache!
    }
    return result
  }

  @JvmStatic
  fun createAsyncDataContext(component: Component?): DataContext {
    if (component == null) return DataContext.EMPTY_CONTEXT
    return PreCachedDataContext(component)
  }

  @JvmStatic
  fun createAsyncDataContext(dataContext: DataContext, provider: Any?): DataContext {
    return when (val asyncContext = createAsyncDataContextImpl(dataContext)) {
      DataContext.EMPTY_CONTEXT -> PreCachedDataContext(null)
        .prependProvider(provider)
      is PreCachedDataContext -> asyncContext
        .prependProvider(provider)
      is CustomizedDataContext -> when (val o = asyncContext.customizedDelegate) {
        is PreCachedDataContext -> o.prependProvider(provider)
        else -> PreCachedDataContext.customize(o as AsyncDataContext, provider)
      }
      is AsyncDataContext -> PreCachedDataContext.customize(asyncContext, provider)
      else -> dataContext.also {
        reportUnexpectedDataContextKind(dataContext)
      }
    }
  }

  @JvmStatic
  private fun createAsyncDataContextImpl(dataContext: DataContext): DataContext = when {
    isAsyncDataContext(dataContext) -> dataContext
    dataContext is EdtDataContext -> createAsyncDataContext(dataContext.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT))
    !ApplicationManager.getApplication().isUnitTestMode() -> dataContext.also {
      reportUnexpectedDataContextKind(dataContext)
    }
    else -> dataContext
  }

  private fun reportUnexpectedDataContextKind(dataContext: DataContext) {
    LOG.error(PluginException.createByClass(
      "Unknown data context kind '${dataContext.javaClass.getName()}'. " +
      "Use DataManager.getDataContext, CustomizedDataContext, or SimpleDataContext", null, dataContext.javaClass))
  }

  @JvmStatic
  @Deprecated("Use `createAsyncDataContext` instead", ReplaceWith("createAsyncDataContext(dataContext)"), DeprecationLevel.ERROR)
  fun wrapDataContext(dataContext: DataContext): DataContext = createAsyncDataContext(dataContext)

  @JvmStatic
  @Deprecated("Use `createAsyncDataContext` instead", ReplaceWith("createAsyncDataContext(dataContext)"), DeprecationLevel.ERROR)
  fun wrapToAsyncDataContext(dataContext: DataContext): DataContext = createAsyncDataContext(dataContext)

  @JvmStatic
  fun isAsyncDataContext(dataContext: DataContext): Boolean {
    return dataContext === DataContext.EMPTY_CONTEXT || dataContext is AsyncDataContext ||
           dataContext is CustomizedDataContext && dataContext.customizedDelegate is AsyncDataContext
  }

  @JvmStatic
  fun checkAsyncDataContext(dataContext: DataContext, place: String) {
    if (!isAsyncDataContext(dataContext)) {
      LOG.error("Cannot convert to AsyncDataContext at '$place' ${dumpDataContextClass(dataContext)}. " +
                "Please use CustomizedDataContext or its inheritors like SimpleDataContext")
    }
  }

  /**
   * Computing fields from data context might be slow and cause freezes.
   * To avoid it, we report only those fields which were already computed
   * in [AnAction.update] or [AnAction.actionPerformed]
   */
  @JvmStatic
  fun getCachedOnlyDataContext(dataContext: DataContext): DataContext {
    return AsyncDataContext { dataId: String -> getRawDataIfCached(dataContext, dataId, false) }
  }

  @JvmStatic
  fun getUiOnlyDataContext(dataContext: DataContext): DataContext {
    return AsyncDataContext { dataId: String -> getRawDataIfCached(dataContext, dataId, true) }
  }

  @JvmStatic
  private fun getRawDataIfCached(dataContext: DataContext, dataId: String, uiOnly: Boolean): Any? = when (dataContext) {
    is PreCachedDataContext -> dataContext.getRawDataIfCached(dataId, uiOnly)
    is EdtDataContext -> dataContext.getRawDataIfCached(dataId)
    else -> null
  }

  @JvmStatic
  fun clearAllCachesAndUpdates() {
    runBlockingForActionExpand {
      cancelAllUpdates("clear-all-caches-and-updates requested")
      waitForAllUpdatesToFinish()
    }
    PreCachedDataContext.clearAllCaches()
  }

  /**
   * The deprecated way to asynchronously expand a group using Promise API
   */
  @ApiStatus.Obsolete
  @JvmStatic
  fun expandActionGroupAsync(group: ActionGroup,
                             presentationFactory: PresentationFactory,
                             context: DataContext,
                             place: String): CancellablePromise<List<AnAction>> {
    return expandActionGroupAsync(group, presentationFactory, context, place, false, true)
  }

  /**
   * The deprecated way to asynchronously expand a group using Promise API
   */
  @ApiStatus.Obsolete
  @JvmStatic
  fun expandActionGroupAsync(group: ActionGroup,
                             presentationFactory: PresentationFactory,
                             context: DataContext,
                             place: String,
                             isToolbarAction: Boolean,
                             fastTrack: Boolean): CancellablePromise<List<AnAction>> {
    return service<CoreUiCoroutineScopeHolder>().coroutineScope.async(Dispatchers.EDT + ModalityState.any().asContextElement() +
                                                                      ClientId.coroutineContext(), CoroutineStart.UNDISPATCHED) {
      expandActionGroupSuspend(group, presentationFactory, context, place, isToolbarAction, fastTrack)
    }.asCompletableFuture().asCancellablePromise()
  }

  /**
   * The preferred way to asynchronously expand a group in a coroutine
   */
  @JvmStatic
  suspend fun expandActionGroupSuspend(group: ActionGroup,
                                       presentationFactory: PresentationFactory,
                                       dataContext: DataContext,
                                       place: String,
                                       isToolbarAction: Boolean,
                                       fastTrack: Boolean): List<AnAction> = withContext(
    CoroutineName("expandActionGroupSuspend ($place)") + ModalityState.any().asContextElement()) {
    ThreadingAssertions.assertEventDispatchThread()
    val asyncDataContext = createAsyncDataContext(dataContext)
    checkAsyncDataContext(asyncDataContext, place)
    val isContextMenu = ActionPlaces.isPopupPlace(place)
    val fastTrackTime = getFastTrackMaxTime(fastTrack, group, place, asyncDataContext, isToolbarAction, true)
    val edtDispatcher =
      if (fastTrackTime > 0) AltEdtDispatcher.apply { switchToQueue() }
      else Dispatchers.EDT[CoroutineDispatcher]!!
    val updater = ActionUpdater(presentationFactory, asyncDataContext, place, isContextMenu, isToolbarAction, edtDispatcher)
    val deferred = async(edtDispatcher, CoroutineStart.UNDISPATCHED) {
      updater.runUpdateSession(updaterContext(place, fastTrackTime, isContextMenu, isToolbarAction)) {
        updater.expandActionGroup(group)
      }
    }
    if (fastTrackTime > 0) {
      AltEdtDispatcher.runOwnQueueBlockingAndSwitchBackToEDT(deferred, fastTrackTime)
    }
    deferred.await()
  }

  /**
   * The preferred way to synchronously expand a group while pumping EDT intended for synchronous clients
   */
  @JvmStatic
  fun expandActionGroup(group: ActionGroup,
                        presentationFactory: PresentationFactory,
                        context: DataContext,
                        place: String): List<AnAction> {
    val point = if (PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(context) == null) null
    else JBPopupFactory.getInstance().guessBestPopupLocation(context)
    var result: List<AnAction>? = null
    val span = getTracer(false).spanBuilder("expandActionGroup").setAttribute("place", place).startSpan()
    val start = System.nanoTime()
    try {
      result = Context.current().with(span).with(OT_ENABLE_SPANS, true).makeCurrent().use {
        expandActionGroupImpl(group, presentationFactory, context, place,
                              ActionPlaces.isPopupPlace(place), point, null, null)
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

  private fun expandActionGroupImpl(group: ActionGroup,
                                    presentationFactory: PresentationFactory,
                                    dataContext: DataContext,
                                    place: String,
                                    isContextMenu: Boolean,
                                    loadingIconPoint: RelativePoint?,
                                    expire: (() -> Boolean)?,
                                    menuItem: Component?): List<AnAction> = runBlockingForActionExpand(
    CoroutineName("expandActionGroupImpl ($place)")) {
    val asyncDataContext = createAsyncDataContext(dataContext)
    checkAsyncDataContext(asyncDataContext, place)
    val isUnitTestMode = ApplicationManager.getApplication().isUnitTestMode
    val maxLoops = max(2, Registry.intValue("actionSystem.update.actions.async.max.nested.loops", 20))
    if (ourExpandActionGroupImplEDTLoopLevel >= maxLoops) {
      LOG.warn("Maximum number of recursive EDT loops reached ($maxLoops) at '$place'")
      cancelAllUpdates("recursive EDT loops limit reached at '$place'")
      throw CancellationException()
    }
    if (isContextMenu) {
      cancelAllUpdates("context menu requested")
    }
    val fastTrackTime = getFastTrackMaxTime(true, group, place, asyncDataContext, false, false)
    val mainJob = coroutineContext.job
    val loopJob = if (isUnitTestMode) null else launch {
      val start = System.nanoTime()
      while (TimeoutUtil.getDurationMillis(start) < fastTrackTime) {
        delay(1)
      }
      runEdtLoop(mainJob, expire, PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(asyncDataContext), menuItem)
    }
    val progressJob = if (loadingIconPoint == null) null else launch {
      addLoadingIcon(loadingIconPoint, place)
    }
    try {
      val edtDispatcher = coroutineContext[CoroutineDispatcher]!!
      val updater = ActionUpdater(presentationFactory, asyncDataContext, place, isContextMenu, false, edtDispatcher)
      updater.runUpdateSession(updaterContext(place, fastTrackTime, isContextMenu, false)) {
        updater.expandActionGroup(group)
      }
    }
    finally {
      progressJob?.cancel()
      loopJob?.cancel()
    }
  }

  fun <T> computeWithProgressIcon(dataContext: DataContext,
                                  place: String,
                                  task: suspend () -> T): T {
    val component = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext)
    val loadingIconPoint = if (component == null) null
    else JBPopupFactory.getInstance().guessBestPopupLocation(dataContext)
    return computeWithProgressIcon(loadingIconPoint, component, place, task)
  }

  fun <T> computeWithProgressIcon(loadingIconPoint: RelativePoint?,
                                  component: Component?,
                                  place: String,
                                  task: suspend () -> T): T = runBlockingForActionExpand(CoroutineName("computeWithProgressIcon")) {
    withProgressIcon(loadingIconPoint, component, place, task)
  }

  suspend fun <T> withProgressIcon(dataContext: DataContext,
                                   place: String,
                                   task: suspend () -> T): T {
    val component = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext)
    val loadingIconPoint = if (component == null) null
    else JBPopupFactory.getInstance().guessBestPopupLocation(dataContext)
    return withProgressIcon(loadingIconPoint, component, place, task)
  }

  @RequiresEdt
  suspend fun <T> withProgressIcon(loadingIconPoint: RelativePoint?,
                                   component: Component?,
                                   place: String,
                                   task: suspend () -> T): T = coroutineScope {
    val mainJob = coroutineContext.job
    val loopJob = launch {
      runEdtLoop(mainJob, null, component, null)
    }
    val progressJob = if (loadingIconPoint == null) null
    else launch {
      addLoadingIcon(loadingIconPoint, place)
    }
    withContext(Dispatchers.Default) {
      try {
        task()
      }
      finally {
        progressJob?.cancel()
        loopJob.cancel()
        SwingUtilities.invokeLater(EmptyRunnable.getInstance())
      }
    }
  }

  fun fillPopupMenu(group: ActionGroup,
                    component: JComponent,
                    presentationFactory: PresentationFactory,
                    context: DataContext,
                    place: String,
                    progressPoint: RelativePoint?) {
    fillMenu(group, component, null, !UISettings.getInstance().disableMnemonics, presentationFactory, context, place,
             false, false, progressPoint, null)
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
    if (LOG.isDebugEnabled) {
      LOG.debug("fillMenu: " + operationName(group, "", place) {
        if (it is ActionIdProvider) it.id
        else if (it is AnAction) ActionManager.getInstance().getId(it)
        else null
      })
    }
    if (ApplicationManagerEx.getApplicationEx().isWriteActionInProgress()) {
      throw ProcessCanceledException()
    }
    if (Thread.holdsLock(component.treeLock)) {
      throw ProcessCanceledException()
    }
    val asyncDataContext = createAsyncDataContext(context)
    checkAsyncDataContext(asyncDataContext, place)
    var result: List<AnAction>? = null
    val span = getTracer(checkNoop = false).spanBuilder("fillMenu").setAttribute("place", place).startSpan()
    val start = System.nanoTime()
    try {
      Context.current().with(span).with(OT_ENABLE_SPANS, true).makeCurrent().use {
        val list = expandActionGroupImpl(group, presentationFactory, asyncDataContext, place, true, progressPoint, expire, component)
        result = list
        if (expire?.invoke() == true) return@use
        val checked = group is CheckedActionGroup
        if (nativePeer == null) {
          fillMenuInner(component as JComponent, list, checked, enableMnemonics,
                        presentationFactory, asyncDataContext, place, isWindowMenu, useDarkIcons)
        }
        else {
          fillMenuInnerMacNative(nativePeer, component as JFrame, list, checked, enableMnemonics,
                                 presentationFactory, asyncDataContext, place, useDarkIcons)
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
      recordActionGroupExpanded(group, asyncDataContext, place, submenu, elapsed, result)
    }
  }

  private suspend fun addLoadingIcon(point: RelativePoint, place: String) {
    val rootPane = UIUtil.getRootPane(point.component)
    val glassPane = (if (rootPane == null) null else rootPane.glassPane as JComponent?) ?: return
    val comp = point.originalComponent
    if ((comp is ActionMenu && comp.getParent() is IdeJMenuBar) ||
        (ActionPlaces.EDITOR_GUTTER_POPUP == place &&
         comp is EditorGutterComponentEx &&
         comp.getGutterRenderer(point.originalPoint) != null)) {
      return
    }

    val isMenuItem = comp is ActionMenu
    val icon = JLabel(if (isMenuItem) AnimatedIcon.Default.INSTANCE else AnimatedIcon.Big.INSTANCE)
    val size = icon.getPreferredSize()
    icon.size = size
    val location = point.getPoint(glassPane)
    if (isMenuItem) {
      location.x -= 3 * size.width
      location.y += (comp.size.height - size.height + 1) / 2
    }
    else {
      location.x -= size.width / 2
      location.y -= size.height / 2
    }
    icon.location = location

    delay(Registry.intValue("actionSystem.popup.progress.icon.delay", 300).toLong())
    try {
      glassPane.add(icon)
      awaitCancellation()
    }
    finally {
      glassPane.remove(icon)
    }
  }

  private fun fillMenuInner(component: JComponent,
                            list: List<AnAction>,
                            checked: Boolean,
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
      var childComponent: JComponent
      if (action is Separator) {
        childComponent = createSeparator(action.text, children.isEmpty())
      }
      else if (action is ActionGroup && !isSubmenuSuppressed(presentation)) {
        val menu = ActionMenu(context, place, action, presentationFactory, enableMnemonics, useDarkIcons)
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
                                     enableMnemonics: Boolean,
                                     presentationFactory: PresentationFactory,
                                     context: DataContext,
                                     place: String,
                                     useDarkIcons: Boolean) {
    val filtered = filterInvisible(list, presentationFactory, place)
    for (action in filtered) {
      val presentation = presentationFactory.getPresentation(action)
      val peer = when {
        action is Separator -> null
        action is ActionGroup && !isSubmenuSuppressed(presentation) -> createMacNativeActionMenu(
          context, place, action, presentationFactory, enableMnemonics, frame, useDarkIcons)
        else -> MacNativeActionMenuItem(
          action, place, context, enableMnemonics, checked, useDarkIcons).apply {
          updateFromPresentation(presentation)
        }.menuItemPeer
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
    val operationName = operationName(action, null, place)
    LOG.error("Invisible menu item for $operationName" +
              ". Most probably caused by async presentation updates that must be avoided")
  }

  @JvmStatic
  fun reportEmptyTextMenuItem(action: AnAction, place: String) {
    val operationName = operationName(action, null, place)
    var message = "Empty menu item text for $operationName"
    if (action.getTemplatePresentation().text.isNullOrEmpty()) {
      message += ". The default action text must be specified in plugin.xml or its class constructor"
    }
    LOG.error(PluginException.createByClass(message, null, action.javaClass))
  }

  @JvmStatic
  fun operationName(action: Any, op: String?, place: String?): String {
    return operationName(action, op, place) { (it as? ActionIdProvider)?.id }
  }

  @JvmStatic
  fun operationName(action: Any, op: String?, place: String?, idProvider: ((Any) -> String?)?): String {
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
      idProvider?.invoke(x)?.let { sb.append("(id=").append(it).append(')') }
      x = x.getDelegate()
      c = x.javaClass
    }
    if (x is String) {
      sb.append(x)
    }
    else {
      sb.append(c.getName()
                  .removePrefix("com.intellij.openapi.actionSystem.")
                  .removePrefix("com.intellij.ide.actions."))
      idProvider?.invoke(x)?.let { sb.append("(id=").append(it).append(')') }
    }
    sb.append(")")
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
  fun isKeepPopupOpen(mode: KeepPopupOnPerform, event: InputEvent?): Boolean = when (mode) {
    KeepPopupOnPerform.Never -> false
    KeepPopupOnPerform.Always -> true
    KeepPopupOnPerform.IfRequested ->
      event is MouseEvent && UIUtil.isControlKeyDown(event)
    KeepPopupOnPerform.IfPreferred ->
      UISettings.getInstance().keepPopupsForToggles ||
      event is MouseEvent && UIUtil.isControlKeyDown(event)
  }

  @JvmStatic
  fun updateMenuItems(popupMenu: JPopupMenu,
                      dataContext: DataContext,
                      place: String,
                      presentationFactory: PresentationFactory) {
    val items = popupMenu.components.filterIsInstance<ActionMenuItem>()
    updateComponentActions(popupMenu, items.map { it.anAction }, dataContext, place, presentationFactory) {
      for (item in items) {
        item.updateFromPresentation(presentationFactory.getPresentation(item.anAction))
      }
    }
  }

  @JvmStatic
  fun updateComponentActions(component: JComponent,
                             actions: Iterable<AnAction>,
                             dataContext: DataContext,
                             place: String,
                             presentationFactory: PresentationFactory,
                             onUpdate: Runnable) {
    val asyncDataContext = createAsyncDataContext(dataContext)
    checkAsyncDataContext(asyncDataContext, place)
    val actionGroup = DefaultActionGroup(actions.toList())
    service<CoreUiCoroutineScopeHolder>().coroutineScope.async(Dispatchers.EDT + ModalityState.any().asContextElement(),
                                                               CoroutineStart.UNDISPATCHED) {
      try {
        expandActionGroupSuspend(group = actionGroup,
                                 presentationFactory = presentationFactory,
                                 dataContext = asyncDataContext,
                                 place = place,
                                 isToolbarAction = false,
                                 fastTrack = true)
        onUpdate.run()
      }
      finally {
        component.repaint()
      }
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
        if (StartupUiUtil.isDarkTheme) {
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

  @JvmStatic
  fun showPopupElapsedMillisIfConfigured(startNanos: Long, component: Component) {
    if (startNanos <= 0 || !Registry.`is`("ide.diagnostics.show.context.menu.invocation.time")) {
      return
    }

    UiNotifyConnector.doWhenFirstShown(component) {
      UIUtil.getWindow(component)?.addWindowListener(object : WindowAdapter() {
        override fun windowOpened(e: WindowEvent) {
          val time = TimeoutUtil.getDurationMillis(startNanos)

          System.getProperty("perf.test.popup.name")?.let { popupName ->
            val startTimeUnixNano = startNanos + StartUpMeasurer.getStartTimeUnixNanoDiff()
            getTracer(false).spanBuilder("popupShown#$popupName")
              .setStartTimestamp(startTimeUnixNano, TimeUnit.NANOSECONDS)
              .startSpan()
              .end(startTimeUnixNano + TimeUnit.MILLISECONDS.toNanos(time), TimeUnit.NANOSECONDS)
          }

          e.window.removeWindowListener(this)
          @Suppress("DEPRECATION", "removal", "HardCodedStringLiteral")
          Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, "Popup invocation took $time ms",
                       NotificationType.INFORMATION).notify(null)
        }
      })
    }
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
    if (e.updateSession !== UpdateSession.EMPTY) return
    val edtDispatcher = Dispatchers.EDT[CoroutineDispatcher]!!
    @Suppress("DEPRECATION", "removal")
    val actionUpdater = ActionUpdater(PresentationFactory(), e.dataContext, e.place,
                                      e.isFromContextMenu, e.isFromActionToolbar, edtDispatcher)
    e.updateSession = actionUpdater.asUpdateSession()
  }

  @Suppress("DEPRECATION", "removal")
  suspend fun <R> withSuspendingUpdateSession(e: AnActionEvent, factory: PresentationFactory,
                                              actionFilter: (AnAction) -> Boolean,
                                              block: suspend CoroutineScope.(SuspendingUpdateSession) -> R): R = coroutineScope {
    val edtDispatcher = Dispatchers.EDT[CoroutineDispatcher]!!
    val dataContext = createAsyncDataContext(e.dataContext)
    checkAsyncDataContext(dataContext, "withSuspendingUpdateSession")
    val updater = ActionUpdater(factory, dataContext, e.place, e.isFromContextMenu, e.isFromActionToolbar, edtDispatcher, actionFilter)
    e.updateSession = updater.asUpdateSession()
    updater.runUpdateSession(updaterContext(e.place, 0, e.isFromContextMenu, e.isFromActionToolbar)) {
      block(e.updateSession as SuspendingUpdateSession)
    }
  }

  fun <R> runWithInputEventEdtDispatcher(contextComponent: Component?, block: suspend CoroutineScope.() -> R): R? {
    val applicationEx = ApplicationManagerEx.getApplicationEx()
    if (ProgressIndicatorUtils.isWriteActionRunningOrPending(applicationEx)) {
      LOG.error("Actions cannot be updated when write-action is running or pending")
      return null
    }
    if (ourInUpdateSessionForInputEventEDTLoop) {
      LOG.warn("Recursive shortcut processing invocation is ignored")
      return null
    }
    val potemkin = PotemkinOverlayProgress(contextComponent)
    ourInUpdateSessionForInputEventEDTLoop = true
    try {
      potemkin.start()
      return runBlockingForActionExpand(CoroutineName("runWithInputEventEdtDispatcher") +
                                        PotemkinElement(potemkin)) {
        val mainJob = coroutineContext.job
        ourCurrentInputEventProcessingJobFlow.value = mainJob
        val potemkinJob = launch(EmptyCoroutineContext, CoroutineStart.UNDISPATCHED) {
          delay(400)
          while (!potemkin.isCanceled) {
            delay(200)
            potemkin.interact()
          }
          mainJob.cancel()
        }
        try {
          block()
        }
        finally {
          potemkinJob.cancel()
        }
      }
    }
    finally {
      ourInUpdateSessionForInputEventEDTLoop = false
      ourCurrentInputEventProcessingJobFlow.value = null
      potemkin.stop()
    }
  }

  // this dispatcher should always be available
  private val cancellationDispatcher = Dispatchers.IO.limitedParallelism(1)
  private var ourCurrentInputEventProcessingJobFlow = MutableStateFlow<Job?>(null)

  /**
   * DO NOT USE. RIDER ONLY!
   * Cancels the current input event processing and runs the provided block.
   *
   * This method ensures that any ongoing input event processing is canceled before running the block.
   * Using to prevent deadlock when EDT is blocked by runWithInputEventEdtDispatcher and important sync call from
   * the backend main thread is requiring to run something on the EDT
   *
   * @param block The suspending function to execute after cancelling the current input event processing.
   * @return The result of the provided block function.
   */
  @DelicateCoroutinesApi
  suspend fun <T> cancelCurrentInputEventProcessingAndRun(block: suspend () -> T): T = coroutineScope {
    val cancelJob = launch(cancellationDispatcher) {
      ourCurrentInputEventProcessingJobFlow.collectLatest {
        it?.cancel()
      }
    }
    val result = block()
    cancelJob.cancel()
    result
  }

  suspend fun <T> runUpdateSessionForInputEvent(actions: List<AnAction>,
                                                inputEvent: InputEvent,
                                                dataContext: DataContext,
                                                place: String,
                                                actionProcessor: ActionProcessor,
                                                factory: PresentationFactory,
                                                function: suspend (List<AnAction>,
                                                                   suspend (AnAction) -> Presentation,
                                                                   Map<Presentation, AnActionEvent>) -> T): T = withContext(
    CoroutineName("runUpdateSessionForInputEvent")) {
    checkAsyncDataContext(dataContext, place)
    val start = System.nanoTime()
    val events = ConcurrentHashMap<Presentation, AnActionEvent>()
    val edtDispatcher = coroutineContext[CoroutineDispatcher]!!
    val actionUpdater = ActionUpdater(factory, dataContext, place, false, false, edtDispatcher) {
      val event = actionProcessor.createEvent(inputEvent, it.dataContext, it.place, it.presentation, it.actionManager)
      events.putIfAbsent(event.presentation, event) ?: event
    }
    cancelAllUpdates("'$place' invoked")

    val result = actionUpdater.runUpdateSession(shortcutUpdateDispatcher) {
      ActionUpdaterInterceptor.runUpdateSessionForInputEvent(actions, place, dataContext, actionUpdater.asUpdateSession()) { promoted ->
        val rearranged = if (promoted.isNotEmpty()) promoted
        else rearrangeByPromoters(actions, dataContext)
        function(rearranged, actionUpdater::presentation, events)
      }
    }
    actionUpdater.applyPresentationChanges()
    val elapsed = TimeoutUtil.getDurationMillis(start)
    if (elapsed > 1000) {
      LOG.warn("$elapsed ms to runUpdateSessionForInputEvent@$place")
    }
    result
  }

  fun rearrangeByPromotersNonAsync(actions: List<AnAction>, dataContext: DataContext): List<AnAction> = runBlockingForActionExpand {
    val asyncDataContext = createAsyncDataContext(dataContext)
    checkAsyncDataContext(asyncDataContext, "rearrangeByPromotersNonAsync")
    rearrangeByPromoters(actions, asyncDataContext)
  }

  fun <R> CoroutineScope.runUpdateSessionForActionSearch(updateSession: UpdateSession,
                                                         block: suspend CoroutineScope.(suspend (AnAction) -> Presentation) -> R): Deferred<R> {
    val updater = ActionUpdater.getUpdater(updateSession) ?: throw AssertionError()
    return async(contextMenuDispatcher + ModalityState.any().asContextElement()) {
      updater.runUpdateSession(CoroutineName("runUpdateSessionForActionSearch (${updater.place})")) {
        block {
          updater.presentation(it)
        }
      }
    }
  }
}

@ApiStatus.Internal
suspend fun rearrangeByPromoters(actions: List<AnAction>, dataContext: DataContext): List<AnAction> {
  val frozenContext = Utils.getUiOnlyDataContext(dataContext)
  return SlowOperations.startSection(SlowOperations.FORCE_ASSERT).use {
    try {
      readActionUndispatchedForActionExpand {
        val promoters = ActionPromoter.EP_NAME.extensionList + actions.filterIsInstance<ActionPromoter>()
        rearrangeByPromotersImpl(actions, frozenContext, promoters)
      }
    }
    catch (ex: CancellationException) {
      throw ex
    }
    catch (e: Throwable) {
      LOG.error(e)
      actions
    }
  }
}

@VisibleForTesting
fun rearrangeByPromotersImpl(actions: List<AnAction>,
                             dataContext: DataContext,
                             promoters: List<ActionPromoter>): List<AnAction> {
  if (promoters.isEmpty()) return actions
  val result = ArrayList(actions)
  val copy = ArrayList(actions)
  var updateCopy = false
  for (promoter in promoters) {
    if (updateCopy) copy.run { clear(); addAll(result); updateCopy = false }
    val promoted = promoter.promote(Collections.unmodifiableList(copy), dataContext)
    if (!promoted.isNullOrEmpty()) {
      result.removeAll(promoted)
      result.addAll(0, promoted)
      updateCopy = true
    }
    val suppressed = promoter.suppress(Collections.unmodifiableList(copy), dataContext)
    if (!suppressed.isNullOrEmpty()) {
      result.removeAll(suppressed)
      updateCopy = true
    }
  }
  result.remove(null)
  return result
}

private fun getFastTrackMaxTime(useFastTrack: Boolean,
                                group: ActionGroup,
                                place: String,
                                context: DataContext,
                                checkLastFailedFastTrackTime: Boolean,
                                checkMainMenuOrToolbarFirstTime: Boolean): Int {
  if (!useFastTrack) return 0
  if (!service<ActionUpdaterInterceptor>().allowsFastUpdate(CommonDataKeys.PROJECT.getData(context), group, place)) {
    return 0
  }
  val mainMenuOrToolbarFirstTime = checkMainMenuOrToolbarFirstTime &&
                                   (ActionPlaces.MAIN_MENU == place || (ExperimentalUI.isNewUI() && ActionPlaces.MAIN_TOOLBAR == place))
  if (mainMenuOrToolbarFirstTime) return 1000 // one second to fully update the main menu or toolbar for the first time
  val result = Registry.intValue("actionSystem.update.actions.async.fast-track.timeout.ms", 50)
  if (checkLastFailedFastTrackTime && lastFailedFastTrackCount > 0 &&
      TimeoutUtil.getDurationMillis(lastFailedFastTrackFinishNanos) < 100) {
    // quickly reduce the fast-track EDT freeze time, if needed.
    // for example, the Debug tool window has more than 20 toolbars
    // if all of them are slow 20 * 50 = 1 second freeze
    // poorly written UI can easily trigger a multi-second freeze
    return result / lastFailedFastTrackCount
  }
  lastFailedFastTrackCount = 0
  return result
}

private class PotemkinElement(val potemkin: PotemkinOverlayProgress) : ThreadContextElement<AccessToken>, IntelliJContextElement {
  companion object : CoroutineContext.Key<PotemkinElement>

  override fun produceChildElement(parentContext: CoroutineContext, isStructured: Boolean): IntelliJContextElement = this

  override val key: CoroutineContext.Key<*> get() = PotemkinElement

  override fun updateThreadContext(context: CoroutineContext): AccessToken {
    if (!EDT.isCurrentThreadEdt()) return AccessToken.EMPTY_ACCESS_TOKEN
    return (ProgressManager.getInstance() as ProgressManagerImpl).withCheckCanceledHook {
      if (!EDT.isCurrentThreadEdt()) return@withCheckCanceledHook
      potemkin.interact()
      runBlockingForActionExpand {} // to give potemkinJob a chance to execute
    }
  }

  override fun restoreThreadContext(context: CoroutineContext, oldState: AccessToken) {
    oldState.finish()
  }

  override fun toString(): String = "PotemkinElement@" + potemkin.hashCode()
}

private fun updaterContext(place: String, fastTrackTime: Int, isContextMenu: Boolean, isToolbarAction: Boolean): CoroutineContext {
  val dispatcher = if (isContextMenu) contextMenuDispatcher
  else if (isToolbarAction && fastTrackTime > 0) toolbarFastDispatcher
  else toolbarDispatcher
  return dispatcher + CoroutineName("ActionUpdater ($place)")
}

// EDT Loop 1: Secondary event loop for context menus and popups
// There is an outer `runBlocking` that runs "computeOnEDT" blocks.
// This loop only handles external UI events - input, focus, and rendering.
suspend fun runEdtLoop(mainJob: Job, expire: (() -> Boolean)?, contextComponent: Component?, menuItem: Component?) {
  val queue = IdeEventQueue.getInstance()
  val window: Window? = if (contextComponent == null) null else SwingUtilities.getWindowAncestor(contextComponent)
  ourExpandActionGroupImplEDTLoopLevel++
  try {
    ThreadingAssertions.assertEventDispatchThread()
    while (true) {
      //runInterruptible()
      // we need `suspend getNextEvent()` API, or at least `getNextEventOrNull(timeout)`
      // because blocking `getNextEvent` prevents "computeOnEDT" blocks from executing.
      // `peekEvent()` + `delay(10)` would do but editor scrolling became noticeably less smooth.
      val event = queue.getNextEvent()
      queue.dispatchEvent(event)
      if (isCancellingExpandEvent(event, window, menuItem) || // TODO can we push back and unwind here?
          expire?.invoke() == true) {
        mainJob.cancel()
      }
      yield()
    }
  }
  finally {
    ourExpandActionGroupImplEDTLoopLevel--
  }
}

private fun isCancellingExpandEvent(event: AWTEvent?, window: Window?, menuItem: Component?) = when (event) {
  is FocusEvent -> event.getID() == FocusEvent.FOCUS_LOST && event.cause == FocusEvent.Cause.ACTIVATION &&
                   window != null && window === SwingUtilities.getWindowAncestor(event.component)
  is KeyEvent -> event.getID() == KeyEvent.KEY_PRESSED
  is MouseEvent -> event.getID() == MouseEvent.MOUSE_PRESSED &&
                   UIUtil.getDeepestComponentAt(event.component, event.x, event.y) !== menuItem
  else -> false
}

// EDT Loop 2: Own queue loop for toolbars with fast-track
// There is no outer `runBlocking` to run "computeOnEdt" blocks.
// They are processed manually until own queue mode is switched off.
private object AltEdtDispatcher : CoroutineDispatcher() {
  private val queue = LinkedBlockingQueue<Runnable>()
  @Volatile
  private var useQueueSemaphore: Semaphore? = null
  private var switchedAt = 0L

  fun switchToQueue() {
    useQueueSemaphore = Semaphore(Integer.MAX_VALUE)
    switchedAt = System.nanoTime()
  }

  override fun dispatch(context: CoroutineContext, block: Runnable) {
    val runnable = ContextAwareRunnable {
      block.run()
    }
    val semaphore = useQueueSemaphore
    if (semaphore?.tryAcquire() == true) {
      try {
        queue.offer(runnable)
      }
      finally {
        semaphore.release()
      }
    }
    else {
      ApplicationManager.getApplication().invokeLater(runnable, ModalityState.any())
    }
  }

  fun runOwnQueueBlockingAndSwitchBackToEDT(job: Job, timeInMillis: Int) {
    try {
      resetThreadContext().use {
        // block EDT for a short and process the explicit EDT queue for update
        while (!job.isCompleted && TimeoutUtil.getDurationMillis(switchedAt) < timeInMillis) {
          val runnable = queue.poll(1, TimeUnit.MILLISECONDS)
          if (runnable != null) {
            runnable.run()
          }
        }
      }
      if (!job.isCompleted) {
        lastFailedFastTrackFinishNanos = System.nanoTime()
        lastFailedFastTrackCount++
      }
    }
    finally {
      val semaphore = useQueueSemaphore!!
      useQueueSemaphore = null
      ArrayList<Runnable>().apply {
        semaphore.acquireUninterruptibly(Integer.MAX_VALUE)
        queue.drainTo(this)
        forEach {
          @Suppress("ForbiddenInSuspectContextMethod")
          ApplicationManager.getApplication().invokeLater(it, ModalityState.any())
        }
      }
      LOG.assertTrue(queue.isEmpty(), "AltEdtDispatcher queue is not empty")
    }
  }
}

// to avoid platform assertions
@Suppress("NOTHING_TO_INLINE")
internal inline fun <R> runBlockingForActionExpand(context: CoroutineContext = EmptyCoroutineContext,
                                                   noinline block: suspend CoroutineScope.() -> R): R = prepareThreadContext { ctx ->
  try {
    @Suppress("RAW_RUN_BLOCKING")
    runBlocking(ctx + context + Context.current().asContextElement(), block)
  }
  catch (pce : ProcessCanceledException) {
    throw pce
  }
  catch (ce: CancellationException) {
    throw CeProcessCanceledException(ce)
  }
}

// to avoid platform assertions
internal suspend inline fun <R> readActionUndispatchedForActionExpand(noinline block: () -> R): R {
  if (!EDT.isCurrentThreadEdt()) {
    return readActionUndispatched(block)
  }
  else {
    return blockingContext { ApplicationManager.getApplication().runReadAction<R, Throwable> { block() } }
  }
}

@ApiStatus.Internal
interface SuspendingUpdateSession: UpdateSession {
  suspend fun presentationSuspend(action: AnAction): Presentation
  suspend fun childrenSuspend(actionGroup: ActionGroup): List<AnAction>
  suspend fun expandSuspend(action: ActionGroup): List<AnAction>

  fun <T : Any?> sharedDataSuspend(key: Key<T>, supplier: suspend () -> T): T

  fun visitCaches(visitor: (AnAction, String, Any) -> Unit)
  fun dropCaches(predicate: (Any) -> Boolean)
  suspend fun <T: Any?> readAction(block: () -> T) : T
}