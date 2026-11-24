// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.picker

import com.intellij.devkit.icons.inspector.DevkitIconsInspectorBundle
import com.intellij.devkit.icons.inspector.model.ComponentIconExtractor
import com.intellij.devkit.icons.inspector.model.IconInspectorControllerImpl
import com.intellij.devkit.icons.inspector.model.PickedIcon
import com.intellij.devkit.icons.inspector.ui.IconInspectorWindows
import com.intellij.ide.DataManager
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.MouseShortcut
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.impl.KeymapManagerImpl
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.AWTEvent
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Window
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities
import javax.swing.Timer

private val LOG = logger<GlobalPickGesture>()

/** Must match the id of the action registered in `intellij.devkit.icons.inspector.xml`. */
internal const val PICK_AT_POINT_ACTION_ID: String = "IconInspector.PickAtPoint"

/** Modifiers a pick gesture can consist of; events without any of them are never a gesture (cheap pre-filter). */
private const val GESTURE_MODIFIERS: Int =
  InputEvent.SHIFT_DOWN_MASK or InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK or InputEvent.META_DOWN_MASK

/** What the global dispatcher does with a mouse event. */
internal enum class GestureMatch {
  /** Not a pick gesture: let the event through. */
  NONE,
  /** The press of a pick gesture: pick the icon under the mouse and consume the event. */
  PICK,
  /** The release/click that follows a gesture press: consume it so the component does not react to half a click. */
  CONSUME,
}

/** Pure matching of mouse events against the pick gesture's mouse shortcuts (from the active keymap). */
internal object PickGestureMatcher {
  fun classify(event: MouseEvent, shortcuts: Collection<MouseShortcut>, isInternal: Boolean): GestureMatch {
    if (!isInternal || shortcuts.isEmpty()) return GestureMatch.NONE
    if (event.id != MouseEvent.MOUSE_PRESSED && event.id != MouseEvent.MOUSE_RELEASED && event.id != MouseEvent.MOUSE_CLICKED) {
      return GestureMatch.NONE
    }
    if (event.clickCount < 1 || event.button == MouseEvent.NOBUTTON) return GestureMatch.NONE
    if (event.modifiersEx and GESTURE_MODIFIERS == 0) return GestureMatch.NONE
    val button = MouseShortcut.getButton(event)
    // a quick second gesture arrives with clickCount 2: treat it as another single-click gesture
    val matches = MouseShortcut(button, event.modifiersEx, event.clickCount) in shortcuts ||
                  event.clickCount > 1 && MouseShortcut(button, event.modifiersEx, 1) in shortcuts
    return when {
      !matches -> GestureMatch.NONE
      event.id == MouseEvent.MOUSE_PRESSED -> GestureMatch.PICK
      else -> GestureMatch.CONSUME
    }
  }

  /**
   * Components the gesture leaves alone: the editor text area, where the default gesture (Ctrl+Alt+Shift+Click) is also
   * "add rectangular selection on mouse drag" (and Quick Evaluate in the XWin keymap). Gutters, tabs, toolbars stay pickable.
   */
  fun isExcludedTarget(component: Component?): Boolean = component is EditorComponentImpl
}

/**
 * Global "pick the icon under the mouse" gesture (default Ctrl+Alt+Shift+Click, see [PICK_AT_POINT_ACTION_ID]; it is a
 * keymap mouse shortcut, so it can be rebound). Works in every window, including modal dialogs, because it is an
 * [IdeEventQueue] dispatcher like `UiMouseAction` of the UI Inspector (Ctrl+Alt+Click), not a listener of a specific window.
 *
 * Installed lazily and only in internal mode: by [PickIconAtPointAction] (instantiated by the keymap on the first gesture),
 * by the Icon Inspector tool window factory when the project opens (it is already internal-gated), and by
 * [com.intellij.devkit.icons.inspector.PickIconAction]. Nothing runs for non-internal users.
 */
@Service(Service.Level.APP)
internal class GlobalPickGesture : Disposable {
  private val installed = AtomicBoolean()

  fun ensureInstalled() {
    if (!ApplicationManager.getApplication().isInternal) return
    if (!installed.compareAndSet(false, true)) return
    IdeEventQueue.getInstance().addDispatcher(IdeEventQueue.NonLockedEventDispatcher(::dispatch), this)
  }

  private fun dispatch(event: AWTEvent): Boolean {
    if (event !is MouseEvent || event.modifiersEx and GESTURE_MODIFIERS == 0) return false
    val match = PickGestureMatcher.classify(event, currentShortcuts(), ApplicationManager.getApplication().isInternal)
    if (match == GestureMatch.NONE) return false
    val source = event.component ?: return false
    val deepest = UIUtil.getDeepestComponentAt(source, event.x, event.y) ?: source
    if (PickGestureMatcher.isExcludedTarget(deepest)) return false
    if (match == GestureMatch.PICK) {
      try {
        pick(deepest, SwingUtilities.convertPoint(source, event.point, deepest), RelativePoint(event))
      }
      catch (t: Throwable) {
        LOG.error(t)
      }
    }
    event.consume()
    return true
  }

  /** Picks the icon at [point] (in [deepest] coordinates) into the Icon Inspector of the component's project. */
  fun pick(deepest: Component, point: Point, hintAt: RelativePoint?) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val project = projectFor(deepest)
    if (project == null) {
      showHint(hintAt, DevkitIconsInspectorBundle.message("dialogs.gesture.no.project"))
      return
    }
    val controller = project.service<IconInspectorControllerImpl>()
    // a running hover picker would otherwise take the next click as well
    controller.cancelPicking()
    OneShotIconPick.run(deepest, point) { picked ->
      if (project.isDisposed) return@run
      if (picked == null) {
        showHint(hintAt, DevkitIconsInspectorBundle.message("dialogs.gesture.no.icon", deepest.javaClass.simpleName))
        return@run
      }
      controller.inspect(picked, newGeneration = true)
      project.service<IconInspectorWindows>().showResults(picked.component)
    }
  }

  override fun dispose() {}

  companion object {
    fun getInstance(): GlobalPickGesture = service()

    fun currentShortcuts(): List<MouseShortcut> {
      if (!KeymapManagerImpl.isKeymapManagerInitialized) return emptyList()
      return KeymapManager.getInstance()?.activeKeymap?.getShortcuts(PICK_AT_POINT_ACTION_ID)?.filterIsInstance<MouseShortcut>()
             ?: emptyList()
    }

    fun projectFor(component: Component): Project? {
      val fromWindow = ProjectUtil.getProjectForComponent(component)
      if (fromWindow != null && !fromWindow.isDisposed) return fromWindow
      val fromData = try {
        CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(component))
      }
      catch (t: Throwable) {
        LOG.debug(t)
        null
      }
      if (fromData != null && !fromData.isDisposed) return fromData
      return ProjectUtil.getActiveProject()
    }

    private fun showHint(at: RelativePoint?, text: @Nls String) {
      if (at == null) return
      JBPopupFactory.getInstance()
        .createHtmlTextBalloonBuilder(text, MessageType.INFO, null)
        .setFadeoutTime(3000)
        .createBalloon()
        .show(at, Balloon.Position.above)
    }
  }
}

/**
 * Picks the icon at one point without hover: Swing icons immediately; inside a Compose host, its icons are made to draw
 * again first (their draws are the only way to locate icons-API icons in Compose) and the hit-test runs when the draws
 * arrive, or after [TIMEOUT_MS] with whatever is known then (e.g. a semantics-only image).
 */
internal class OneShotIconPick private constructor(
  private val deepest: Component,
  private val point: Point,
  private val onResult: (PickedIcon?) -> Unit,
) {
  private val tracker = ComposeHitTracker(::onDraws)
  private var timer: Timer? = null
  private var done = false

  private fun start() {
    if (!tracker.isCompose(deepest)) {
      complete(safeFind { ComponentIconExtractor.findIcon(deepest, point) })
      return
    }
    tracker.open()
    val first = safeFind { tracker.find(deepest, point) }
    if (first?.compose?.capture != null) {
      complete(first)
      return
    }
    timer = Timer(TIMEOUT_MS) { complete(safeFind { tracker.find(deepest, point) }) }.apply {
      isRepeats = false
      start()
    }
  }

  private fun onDraws() {
    if (done) return
    val found = safeFind { tracker.find(deepest, point) }
    if (found?.compose?.capture != null) complete(found)
  }

  private fun complete(result: PickedIcon?) {
    if (done) return
    done = true
    timer?.stop()
    timer = null
    tracker.close()
    try {
      onResult(result)
    }
    catch (t: Throwable) {
      LOG.error(t)
    }
  }

  private inline fun safeFind(find: () -> PickedIcon?): PickedIcon? = try {
    find()
  }
  catch (t: Throwable) {
    LOG.debug(t)
    null
  }

  companion object {
    private const val TIMEOUT_MS = 500

    fun run(deepest: Component, point: Point, onResult: (PickedIcon?) -> Unit) {
      OneShotIconPick(deepest, point, onResult).start()
    }
  }
}

/**
 * The pick gesture as an action, so its mouse shortcut lives in the keymap (default Ctrl+Alt+Shift+Click, rebindable) and it
 * can also get a keyboard shortcut: invoked from the keyboard it picks the icon under the mouse pointer. Enabled in modal
 * contexts, so the keymap fires it inside modal dialogs too. Normally the [GlobalPickGesture] dispatcher consumes the
 * gesture before the keymap sees it; this action is the path for the very first gesture (the keymap instantiates it, which
 * installs the dispatcher) and for keyboard invocations.
 */
internal class PickIconAtPointAction : DumbAwareAction() {
  init {
    isEnabledInModalContext = true
    GlobalPickGesture.getInstance().ensureInstalled()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val event = e.inputEvent as? MouseEvent
    val excluded = event?.component?.let { PickGestureMatcher.isExcludedTarget(UIUtil.getDeepestComponentAt(it, event.x, event.y)) } == true
    e.presentation.isEnabled = ApplicationManager.getApplication().isInternal && !excluded
  }

  override fun actionPerformed(e: AnActionEvent) {
    val event = e.inputEvent as? MouseEvent
    val source = event?.component
    if (event != null && source != null) {
      val deepest = UIUtil.getDeepestComponentAt(source, event.x, event.y) ?: source
      GlobalPickGesture.getInstance().pick(deepest, SwingUtilities.convertPoint(source, event.point, deepest), RelativePoint(event))
      return
    }
    val (deepest, point) = componentUnderPointer() ?: return
    GlobalPickGesture.getInstance().pick(deepest, point, RelativePoint(deepest, point))
  }

  /** The deepest component under the mouse pointer in the top-most showing window there, with the point in its coordinates. */
  private fun componentUnderPointer(): Pair<Component, Point>? {
    val screenPoint = MouseInfo.getPointerInfo()?.location ?: return null
    val candidates = Window.getWindows().filter { window ->
      window.isShowing && window.bounds.contains(screenPoint)
    }
    if (candidates.isEmpty()) return null
    val active = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
    // owned windows (popups, dialogs) are above their owners
    val window = candidates.maxWith(compareBy<Window> { ownerDepth(it) }.thenBy { it === active })
    val root = (window as? RootPaneContainer)?.rootPane
    // search the layered pane, not the window: a visible IdeGlassPane would otherwise be the deepest component
    val container: Component = root?.layeredPane ?: window
    val inContainer = Point(screenPoint).also { SwingUtilities.convertPointFromScreen(it, container) }
    var deepest = UIUtil.getDeepestComponentAt(container, inContainer.x, inContainer.y) ?: return null
    if (deepest is IdeGlassPane) deepest = root?.contentPane ?: return null
    val inDeepest = Point(screenPoint).also { SwingUtilities.convertPointFromScreen(it, deepest) }
    return deepest to inDeepest
  }

  private fun ownerDepth(window: Window): Int {
    var depth = 0
    var current = window.owner
    while (current != null && depth < 32) {
      depth++
      current = current.owner
    }
    return depth
  }
}
