// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.openapi.wm.impl

import com.intellij.ide.IdeEventQueue
import com.intellij.ide.IdeTooltipManager
import com.intellij.ide.dnd.DnDAware
import com.intellij.idea.AppMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.MouseWheelEventInterceptor.Companion.MOUSE_WHEEL_EVENT_INTERCEPTORS
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.impl.ProjectLoadingCancelled
import com.intellij.openapi.ui.AbstractPainter
import com.intellij.openapi.ui.Divider
import com.intellij.openapi.ui.Painter
import com.intellij.openapi.ui.impl.GlassPaneDialogWrapperPeer.TransparentLayeredPane
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.Weighted
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.platform.ide.bootstrap.hasSplash
import com.intellij.platform.ide.bootstrap.hideSplash
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer
import com.intellij.ui.ClientProperty
import com.intellij.ui.ComponentUtil
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.ui.*
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.event.*
import java.util.*
import javax.swing.*
import javax.swing.text.html.HTMLEditorKit
import kotlin.coroutines.EmptyCoroutineContext

class IdeGlassPaneImpl : JComponent, IdeGlassPaneEx, IdeEventQueue.NonLockedEventDispatcher {
  private val mouseListeners = ArrayList<EventListener>()

  private val sortedMouseListeners = TreeSet<EventListener> { o1, o2 ->
    val weight1 = if (o1 is Weighted) o1.weight else 0.0
    val weight2 = if (o2 is Weighted) o2.weight else 0.0
    if (weight1 > weight2) 1 else if (weight1 < weight2) -1 else mouseListeners.indexOf(o1) - mouseListeners.indexOf(o2)
  }

  private val pane: JRootPane
  private val namedPainters = HashMap<String, PainterHelper>()
  private var isPreprocessorActive = false
  private val listenerToCursor = LinkedHashMap<Any, Cursor>()
  private var lastCursorComponent: Component? = null
  private var lastOriginalCursor: Cursor? = null
  private var prevPressEvent: MouseEvent? = null

  @Suppress("MemberVisibilityCanBePrivate")
  @JvmField
  internal var windowShadowPainter: AbstractPainter? = null
  private var paintersInstalled = false
  private var loadingIndicator: IdePaneLoadingLayer? = null

  internal var colorfulToolbar: (() -> Boolean)? = null

  @JvmOverloads
  constructor(rootPane: JRootPane, installPainters: Boolean = false) {
    pane = rootPane
    isOpaque = false
    isVisible = false
    // workaround to fix cursor when some semi-transparent 'highlighting area' overrides it to default
    isEnabled = false
    layout = null
    if (installPainters) {
      installPainters()
    }
  }

  internal constructor(rootPane: JRootPane, loadingState: FrameLoadingState?, coroutineScope: CoroutineScope) {
    pane = rootPane
    isOpaque = false

    // workaround to fix cursor when some semi-transparent 'highlighting area' overrides it to default
    isEnabled = false

    if (AppMode.isHeadless() || ApplicationManager.getApplication().isHeadlessEnvironment) {
      isVisible = false
    }
    else if (loadingState == null || loadingState.done.isCompleted) {
      isVisible = false
      hideSplash()
      FUSProjectHotStartUpMeasurer.reportFrameBecameInteractive()
    }
    else if (hasSplash()) {
      loadingState.done.invokeOnCompletion {
        FUSProjectHotStartUpMeasurer.reportFrameBecameInteractive()
        coroutineScope.launch(RawSwingDispatcher) {
          hideSplash()
        }
      }
    }
    else {
      hideSplash()
      loadingIndicator = IdePaneLoadingLayer(pane = this, loadingState, coroutineScope = coroutineScope) {
        FUSProjectHotStartUpMeasurer.reportFrameBecameInteractive()
        loadingIndicator = null
        applyActivationState()
      }
      applyActivationState()
    }
  }

  companion object {
    fun hasPreProcessedCursor(component: JComponent): Boolean = component.getClientProperty(PREPROCESSED_CURSOR_KEY) != null

    @JvmStatic
    fun savePreProcessedCursor(component: JComponent, cursor: Cursor): Boolean {
      if (hasPreProcessedCursor(component)) {
        return false
      }
      component.putClientProperty(PREPROCESSED_CURSOR_KEY, cursor)
      return true
    }

    fun forgetPreProcessedCursor(component: JComponent) {
      component.putClientProperty(PREPROCESSED_CURSOR_KEY, null)
    }
  }

  override fun doLayout() {
    loadingIndicator?.icon?.let { icon ->
      val iconSize = icon.preferredSize
      icon.setBounds((width - iconSize.width) / 2, (height - iconSize.height) / 2, iconSize.width, iconSize.height)
    }
  }

  @ApiStatus.Experimental
  @ApiStatus.Internal
  fun addFallbackBackgroundPainter(fallbackBackgroundPainter : Painter) {
    installPainters()
    IdeBackgroundUtil.addFallbackBackgroundPainter(this, fallbackBackgroundPainter)
  }

  internal fun installPainters() {
    if (paintersInstalled) {
      return
    }

    paintersInstalled = true
    IdeBackgroundUtil.initFramePainters(this)
    IdeBackgroundUtil.initEditorPainters(this)
    if (SystemInfoRt.isWindows && java.lang.Boolean.getBoolean("ide.window.shadow.painter")) {
      windowShadowPainter = WindowShadowPainter()
      painters.addPainter(windowShadowPainter!!, null)
    }
  }

  override fun dispatch(e: AWTEvent): Boolean {
    if (e !is InputEvent) {
      return false
    }

    if (e is MouseWheelEvent) {
      // Use extension point processors
      MOUSE_WHEEL_EVENT_INTERCEPTORS.extensionList.forEach { processor ->
        if (processor.process(e)) {
          logger<IdeGlassPaneImpl>().debug("Mouse wheel event $e is processed by $processor. Propagation is stopped")
          return true
        }
      }
    }

    loadingIndicator?.let {
      if (!LaterInvocator.isInModalContext() && it.handleInputEvent(e)) {
        return true
      }
    }
    return e is MouseEvent && dispatchMouseEvent(e)
  }

  private fun dispatchMouseEvent(event: MouseEvent): Boolean {
    val eventRootPane = pane
    val eventWindow = ComponentUtil.getWindow(event.component)
    if (isContextMenu(eventWindow)) {
      return false
    }

    val thisGlassWindow = SwingUtilities.getWindowAncestor(pane)
    if (eventWindow !== thisGlassWindow) {
      return false
    }

    if (event.id == MouseEvent.MOUSE_DRAGGED) {
      if (ApplicationManager.getApplication() != null) {
        IdeTooltipManager.getInstance().hideCurrent(event)
      }
    }

    var dispatched: Boolean
    dispatched = when (event.id) {
      MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED -> preprocess(event, false, eventRootPane)
      MouseEvent.MOUSE_MOVED, MouseEvent.MOUSE_DRAGGED -> preprocess(event, true, eventRootPane)
      MouseEvent.MOUSE_EXITED, MouseEvent.MOUSE_ENTERED -> preprocess(event, false, eventRootPane)
      else -> return false
    }

    val meComponent = event.component
    if (!dispatched && meComponent != null) {
      if (eventWindow !== SwingUtilities.getWindowAncestor(pane)) {
        return false
      }

      @Suppress("DEPRECATION")
      val button1 = InputEvent.BUTTON1_MASK or InputEvent.BUTTON1_DOWN_MASK
      val pureMouse1Event = event.modifiersEx or button1 == button1
      if (pureMouse1Event && event.clickCount <= 1 && !event.isPopupTrigger) {
        val parent = pane.contentPane.parent
        val point = SwingUtilities.convertPoint(meComponent, event.point, parent)
        val target = SwingUtilities.getDeepestComponentAt(parent, point.x, point.y)
        dispatched = target is DnDAware && dispatchForDnDAware(event, point, target)
      }
    }
    if (isVisible && componentCount == 0) {
      var cursorSet = false
      if (meComponent != null) {
        val parent = pane.contentPane.parent
        val point = SwingUtilities.convertPoint(meComponent, event.point, parent)
        val target = SwingUtilities.getDeepestComponentAt(parent, point.x, point.y)
        if (target != null) {
          UIUtil.setCursor(this, target.cursor)
          cursorSet = true
        }
      }
      if (!cursorSet) {
        UIUtil.setCursor(this, Cursor.getDefaultCursor())
      }
    }
    return dispatched
  }

  private fun dispatchForDnDAware(event: MouseEvent, point: Point, target: Component): Boolean {
    val targetPoint = SwingUtilities.convertPoint(pane.contentPane.parent, point.x, point.y, target)
    val overSelection = (target as DnDAware).isOverSelection(targetPoint)
    if (!overSelection) {
      return false
    }

    when (event.id) {
      MouseEvent.MOUSE_PRESSED -> {
        if (target.isFocusable) {
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown {
            IdeFocusManager.getGlobalInstance().requestFocus(target, true)
          }
        }

        var consumed = false
        val mouseEvent = MouseEventAdapter.convert(event, target)
        for (listener in target.getListeners(MouseListener::class.java)) {
          val className = listener.javaClass.name
          if (className.contains("BasicTreeUI$") || className.contains("MacTreeUI$")) {
            continue
          }

          fireMouseEvent(listener = listener, event = mouseEvent)
          if (mouseEvent.isConsumed) {
            consumed = true
            break
          }
        }

        if (!mouseEvent.isConsumed) {
          val eventListeners = Toolkit.getDefaultToolkit().getAWTEventListeners(AWTEvent.MOUSE_EVENT_MASK)
          if (eventListeners != null && eventListeners.isNotEmpty()) {
            for (eventListener in eventListeners) {
              eventListener.eventDispatched(event)
              if (event.isConsumed) {
                break
              }
            }
            if (event.isConsumed) {
              return false
            }
          }
        }
        if (consumed) {
          event.consume()
        }
        else {
          prevPressEvent = mouseEvent
        }
        return true
      }
      MouseEvent.MOUSE_RELEASED -> {
        return dispatchMouseReleased(event, target)
      }
      else -> {
        prevPressEvent = null
        return false
      }
    }
  }

  private fun dispatchMouseReleased(event: MouseEvent, target: Component): Boolean {
    val mouseEvent = MouseEventAdapter.convert(event, target)
    if (prevPressEvent == null || prevPressEvent!!.component !== target) {
      return false
    }

    for (listener in target.getListeners(MouseListener::class.java)) {
      val className = listener.javaClass.name
      if (className.contains("BasicTreeUI$") || className.contains("MacTreeUI$")) {
        fireMouseEvent(listener, prevPressEvent!!)
        fireMouseEvent(listener, mouseEvent)
        if (mouseEvent.isConsumed) {
          break
        }
      }
      fireMouseEvent(listener, mouseEvent)
      if (mouseEvent.isConsumed) {
        break
      }
    }
    if (mouseEvent.isConsumed) {
      event.consume()
    }
    prevPressEvent = null
    return true
  }

  private fun preprocess(e: MouseEvent, motion: Boolean, eventRootPane: JRootPane): Boolean {
    try {
      if (ComponentUtil.getWindow(this) !== ComponentUtil.getWindow(e.component)) {
        return false
      }

      val event = MouseEventAdapter.convert(e, eventRootPane)
      if (event.isAltDown && SwingUtilities.isLeftMouseButton(event) && event.id == MouseEvent.MOUSE_PRESSED) {
        val c = SwingUtilities.getDeepestComponentAt(e.component, e.x, e.y)
        val component = ComponentUtil.findParentByCondition(c) { ClientProperty.isTrue(it, UIUtil.TEXT_COPY_ROOT) }
        if (component != null) {
          CopyPasteManager.copyTextToClipboard(UIUtil.getDebugText(component))
        }
      }

      if (!IdeGlassPaneUtil.canBePreprocessed(e)) {
        return false
      }

      for (each in sortedMouseListeners) {
        if (motion && each is MouseMotionListener) {
          fireMouseMotion(each, event)
        }
        else if (!motion && each is MouseListener) {
          fireMouseEvent(each, event)
        }
        if (event.isConsumed) {
          e.consume()
          return true
        }
      }
      return false
    }
    finally {
      if (eventRootPane === pane) {
        if (!listenerToCursor.isEmpty()) {
          val cursor = listenerToCursor.values.iterator().next()
          val point = SwingUtilities.convertPoint(e.component, e.point, pane.contentPane.parent)
          var target = SwingUtilities.getDeepestComponentAt(pane.contentPane.parent, point.x, point.y)
          if (canProcessCursorFor(target)) {
            target = getCompWithCursor(target)
            restoreLastComponent(target)
            if (target != null) {
              if (lastCursorComponent !== target) {
                lastCursorComponent = target
                lastOriginalCursor = target.cursor
              }
              if (cursor != target.cursor) {
                setCursor(target, cursor)
              }
            }
            UIUtil.setCursor(pane, cursor)
          }
        }
        else if (!e.isConsumed && e.id != MouseEvent.MOUSE_DRAGGED) {
          val cursor = Cursor.getDefaultCursor()
          UIUtil.setCursor(pane, cursor)
          restoreLastComponent(null)
          lastOriginalCursor = null
          lastCursorComponent = null
        }
        listenerToCursor.clear()
      }
    }
  }

  private fun restoreLastComponent(newC: Component?) {
    if (lastCursorComponent != null && lastCursorComponent !== newC) {
      resetCursor(lastCursorComponent!!, lastOriginalCursor)
    }
  }

  override fun setCursor(cursor: Cursor?, requestor: Any) {
    if (cursor == null) {
      listenerToCursor.remove(requestor)
    }
    else {
      listenerToCursor.put(requestor, cursor)
    }
  }

  override fun addMousePreprocessor(listener: MouseListener, parent: Disposable) {
    doAddListener(listener, parent)
  }

  override fun addMouseListener(listener: MouseListener, coroutineScope: CoroutineScope) {
    mouseListeners.add(listener)
    executeOnCancelInEdt(coroutineScope) { removeListener(listener) }
    updateSortedList()
    activateIfNeeded()
  }

  override fun addMouseMotionPreprocessor(listener: MouseMotionListener, parent: Disposable) {
    doAddListener(listener, parent)
  }

  private fun doAddListener(listener: EventListener, parent: Disposable) {
    mouseListeners.add(listener)
    Disposer.register(parent) { EdtInvocationManager.invokeLaterIfNeeded { removeListener(listener) } }
    updateSortedList()
    activateIfNeeded()
  }

  private fun removeListener(listener: EventListener) {
    if (mouseListeners.remove(listener)) {
      updateSortedList()
    }
    deactivateIfNeeded()
  }

  private fun updateSortedList() {
    sortedMouseListeners.clear()
    sortedMouseListeners.addAll(mouseListeners)
  }

  private fun deactivateIfNeeded() {
    if (isPreprocessorActive && mouseListeners.isEmpty()) {
      isPreprocessorActive = false
    }
    applyActivationState()
  }

  private fun activateIfNeeded() {
    if (!isPreprocessorActive && !mouseListeners.isEmpty()) {
      isPreprocessorActive = true
    }
    applyActivationState()
  }

  private fun applyActivationState() {
    val wasVisible = isVisible
    val hasWork = loadingIndicator != null || painters.hasPainters() || componentCount > 0
    if (wasVisible != hasWork) {
      isVisible = hasWork
    }
    val queue = IdeEventQueue.getInstance()
    val containsDispatcher = queue.containsDispatcher(this)
    if (!containsDispatcher && (isPreprocessorActive || isVisible)) {
      queue.addDispatcher(this, null)
    }
    else if (containsDispatcher && !isPreprocessorActive && !isVisible) {
      queue.removeDispatcher(this)
    }
    if (wasVisible != isVisible) {
      revalidate()
      repaint()
    }
  }

  @Suppress("MemberVisibilityCanBePrivate")
  @JvmName("getNamedPainters")
  internal fun getNamedPainters(name: String): PainterHelper {
    return namedPainters.computeIfAbsent(name) { PainterHelper(this) }
  }

  private val painters: PainterHelper
    get() = getNamedPainters("glass")

  override fun addPainter(component: Component?, painter: Painter, parent: Disposable) {
    painters.addPainter(painter, component)
    activateIfNeeded()
    Disposer.register(parent) { SwingUtilities.invokeLater { removePainter(painter) } }
  }

  private fun removePainter(painter: Painter) {
    painters.removePainter(painter)
    deactivateIfNeeded()
  }

  override fun addImpl(comp: Component, constraints: Any?, index: Int) {
    super.addImpl(comp, constraints, index)

    SwingUtilities.invokeLater(::activateIfNeeded)
  }

  override fun remove(comp: Component) {
    super.remove(comp)

    SwingUtilities.invokeLater(::deactivateIfNeeded)
  }

  override fun isInModalContext(): Boolean {
    return components.any { it is TransparentLayeredPane }
  }

  override fun isColorfulToolbar(): Boolean {
    return colorfulToolbar?.invoke() == true
  }

  override fun paintComponent(g: Graphics) {
    if (loadingIndicator == null) {
      painters.paint(g)
    }
  }

  fun getTargetComponentFor(e: MouseEvent): Component {
    return findComponent(e = e, container = pane.layeredPane)
           ?: findComponent(e, pane.contentPane)
           ?: e.component
  }

  override fun isOptimizedDrawingEnabled(): Boolean {
    return !painters.hasPainters() && super.isOptimizedDrawingEnabled()
  }
}

private class IdePaneLoadingLayer(pane: JComponent,
                                  private val loadingState: FrameLoadingState,
                                  private val coroutineScope: CoroutineScope,
                                  private val onFinish: () -> Unit) {
  @JvmField
  val icon: AnimatedIcon = AsyncProcessIcon.createBig(coroutineScope)

  init {
    icon.isOpaque = false
    pane.add(icon)

    val startUpContextElementToPass = FUSProjectHotStartUpMeasurer.getStartUpContextElementToPass() ?: EmptyCoroutineContext
    loadingState.done.invokeOnCompletion {
      coroutineScope.launch(RawSwingDispatcher + startUpContextElementToPass) {
        try {
          removeIcon(pane)
        }
        finally {
          onFinish()
        }

        coroutineScope.cancel()
      }
    }
  }

  private fun removeIcon(pane: JComponent) {
    pane.remove(icon)
  }

  fun handleInputEvent(event: InputEvent): Boolean {
    val loadingJob = loadingState.done
    if (loadingJob.isCompleted) {
      return false
    }

    @Suppress("DuplicatedCode")
    return when (event) {
      is MouseEvent -> {
        event.consume()
        true
      }
      is KeyEvent -> {
        @Suppress("DEPRECATION")
        if (event.getID() == KeyEvent.KEY_PRESSED && event.keyCode == KeyEvent.VK_ESCAPE && event.modifiers == 0) {
          loadingJob.cancel(ProjectLoadingCancelled("ESC key pressed"))
        }

        event.consume()
        true
      }
      else -> false
    }
  }
}

internal interface FrameLoadingState {
  val done: Job
}

@OptIn(AwaitCancellationAndInvoke::class)
internal fun executeOnCancelInEdt(coroutineScope: CoroutineScope, task: () -> Unit) {
  coroutineScope.awaitCancellationAndInvoke {
    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      task()
    }
  }
}

private const val PREPROCESSED_CURSOR_KEY = "SuperCursor"

private fun isContextMenu(window: Window?): Boolean {
  if (window is JWindow) {
    for (component in window.layeredPane.components) {
      if (component is JPanel && component.components.any { it is JPopupMenu }) {
        return true
      }
    }
  }
  return false
}

private fun setCursor(target: Component, cursor: Cursor) {
  if (target is EditorComponentImpl) {
    target.editor.setCustomCursor(IdeGlassPaneImpl::class.java, cursor)
  }
  else {
    if (target is JComponent) {
      IdeGlassPaneImpl.savePreProcessedCursor(target, target.getCursor())
    }
    UIUtil.setCursor(target, cursor)
  }
}

private fun resetCursor(target: Component, lastCursor: Cursor?) {
  if (target is EditorComponentImpl) {
    target.editor.setCustomCursor(IdeGlassPaneImpl::class.java, null)
  }
  else {
    var cursor: Cursor? = null
    if (target is JComponent) {
      cursor = target.getClientProperty(PREPROCESSED_CURSOR_KEY) as Cursor?
      target.putClientProperty(PREPROCESSED_CURSOR_KEY, null)
    }
    UIUtil.setCursor(target, cursor ?: lastCursor)
  }
}

private fun canProcessCursorFor(target: Component?): Boolean {
  return target !is JMenuItem &&
         target !is Divider &&
         target !is JSeparator &&
         !(target is JEditorPane && target.editorKit is HTMLEditorKit)
}

private fun getCompWithCursor(c: Component?): Component? {
  var eachParentWithCursor = c
  while (eachParentWithCursor != null) {
    if (eachParentWithCursor.isCursorSet) {
      return eachParentWithCursor
    }
    eachParentWithCursor = eachParentWithCursor.parent
  }
  return null
}

private fun fireMouseEvent(listener: MouseListener, event: MouseEvent) {
  when (event.id) {
    MouseEvent.MOUSE_PRESSED -> listener.mousePressed(event)
    MouseEvent.MOUSE_RELEASED -> listener.mouseReleased(event)
    MouseEvent.MOUSE_ENTERED -> listener.mouseEntered(event)
    MouseEvent.MOUSE_EXITED -> listener.mouseExited(event)
    MouseEvent.MOUSE_CLICKED -> listener.mouseClicked(event)
  }
}

private fun fireMouseMotion(listener: MouseMotionListener, event: MouseEvent) {
  when (event.id) {
    MouseEvent.MOUSE_DRAGGED -> {
      listener.mouseDragged(event)
      listener.mouseMoved(event)
    }
    MouseEvent.MOUSE_MOVED -> listener.mouseMoved(event)
  }
}

private fun findComponent(e: MouseEvent, container: Container): Component? {
  val lpPoint = SwingUtilities.convertPoint(e.component, e.point, container)
  return SwingUtilities.getDeepestComponentAt(container, lpPoint.x, lpPoint.y)
}