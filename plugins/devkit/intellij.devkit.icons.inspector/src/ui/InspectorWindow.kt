// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.ui

import com.intellij.devkit.icons.inspector.DevkitIconsInspectorBundle
import com.intellij.devkit.icons.inspector.ICON_INSPECTOR_TOOLWINDOW_ID
import com.intellij.devkit.icons.inspector.model.IconInspectorController
import com.intellij.devkit.icons.inspector.model.IconInspectorControllerImpl
import com.intellij.devkit.icons.inspector.model.InspectorState
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.ComponentUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.Component
import java.awt.Dialog
import java.awt.KeyboardFocusManager
import java.awt.Rectangle
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.WeakHashMap
import javax.swing.JComponent

/** Where the inspector UI is shown for a pick. */
internal sealed interface ResultsHost<out W> {
  /** The Icon Inspector tool window ([InspectorMode.DOCKED], no modal dialog involved). */
  data object ToolWindow : ResultsHost<Nothing>

  /**
   * [InspectorMode.DOCKED] only: a separate non-modal window owned by the modal dialog [owner]. The tool window's frame is
   * blocked by that dialog, while windows owned by it are not. There is one such window per modal dialog.
   */
  data class OwnedWindow<W>(val owner: W) : ResultsHost<W>

  /**
   * [InspectorMode.DETACHED]: the single detached window ([DetachedWindowSlot]). [owner] is `null` for the ownerless window,
   * which is excluded from modality. It is the modal dialog to move the window into only when the toolkit cannot exclude it.
   */
  data class DetachedWindow<W>(val owner: W?) : ResultsHost<W>
}

/** Window selection, generic over the window type so it is testable without AWT windows (headless). */
internal object ResultsHostSelection {
  private const val MAX_OWNER_DEPTH = 32

  /** The first showing modal dialog among [start] and its owners (nearest first), or `null`. */
  fun <W : Any> modalOwner(start: W?, ownerOf: (W) -> W?, isShowingModalDialog: (W) -> Boolean): W? {
    var current = start
    var depth = 0
    while (current != null && depth++ < MAX_OWNER_DEPTH) {
      if (isShowingModalDialog(current)) return current
      current = ownerOf(current)
    }
    return null
  }

  fun <W : Any> select(
    start: W?,
    mode: InspectorMode,
    modalExclusionSupported: Boolean,
    ownerOf: (W) -> W?,
    isShowingModalDialog: (W) -> Boolean,
  ): ResultsHost<W> {
    val modalOwner = modalOwner(start, ownerOf, isShowingModalDialog)
    return when (mode) {
      InspectorMode.DOCKED -> modalOwner?.let { ResultsHost.OwnedWindow(it) } ?: ResultsHost.ToolWindow
      InspectorMode.DETACHED -> ResultsHost.DetachedWindow(if (modalExclusionSupported) null else modalOwner)
    }
  }

  /** AWT flavor: [start] is the picked component's window (or the active window). */
  fun select(start: Window?, mode: InspectorMode, modalExclusionSupported: Boolean): ResultsHost<Window> =
    select(start, mode, modalExclusionSupported, { it.owner }, ::isShowingModalDialog)

  fun isShowingModalDialog(window: Window): Boolean = window is Dialog && window.isModal && window.isShowing
}

/**
 * Routes the Icon Inspector UI of a project. The [mode] decides the normal place:
 * - [InspectorMode.DOCKED]: the tool window. When the pick happens in a modal dialog (or a window owned by one), a separate
 *   non-modal [IconInspectorDialog] owned by that dialog shows it instead, one per dialog, closed with it.
 * - [InspectorMode.DETACHED]: exactly one separate window, also for picks in modal dialogs. Normally it is the ownerless
 *   [IconInspectorFrame], excluded from modality, so it stays usable while modal dialogs are open. If the toolkit cannot
 *   exclude it, the [DetachedWindowSlot] moves the inspector into one [IconInspectorDialog] owned by the modal dialog,
 *   and back when that dialog closes.
 *
 * All windows host the same Compose content for the same project controller, so a mode switch keeps the current inspection.
 *
 * Besides explicit [showResults] calls (global gesture, Pick action), [ensureWatching] follows the controller: a pick that
 * completes while a modal dialog is active (e.g. the hover picker started before the dialog opened) also opens the window.
 */
@Service(Service.Level.PROJECT)
internal class IconInspectorWindows(private val project: Project, private val coroutineScope: CoroutineScope) : Disposable {
  private val ownedWindows = WeakHashMap<Window, IconInspectorDialog>()
  private val detached = DetachedWindowSlot(DetachedHosts())
  private val modalExclusionSupported: Boolean by lazy { isModalExclusionSupported() }
  private var watching = false
  private var disposed = false

  private val controller: IconInspectorControllerImpl
    get() = project.service<IconInspectorControllerImpl>()

  /** The remembered mode. On project open, the detached window opens only when the user opens the inspector. */
  var mode: InspectorMode
    get() = if (PropertiesComponent.getInstance(project).getBoolean(DETACHED_PROPERTY)) InspectorMode.DETACHED else InspectorMode.DOCKED
    private set(value) = PropertiesComponent.getInstance(project).setValue(DETACHED_PROPERTY, value == InspectorMode.DETACHED)

  /** Shows the inspector for a pick of [pickedComponent] (or, if `null`, in the context of the active window). */
  fun showResults(pickedComponent: Component?) {
    ensureWatching()
    show(select(windowOf(pickedComponent)))
  }

  /** Whether the inspector for [component] (or the active window) is in a separate window, not in the tool window. */
  fun usesSeparateWindow(component: Component?): Boolean =
    when (select(windowOf(component))) {
      ResultsHost.ToolWindow -> false
      is ResultsHost.OwnedWindow, is ResultsHost.DetachedWindow -> true
    }

  /** Switches to [InspectorMode.DETACHED]: hides the tool window and shows the inspector in the detached window. */
  fun detach() {
    ensureWatching()
    mode = InspectorMode.DETACHED
    // the detached window replaces the windows owned by modal dialogs, so that only one inspector window exists
    ownedWindows.values.toList().forEach(::closeIfOpen)
    show(select(windowOf(null)))
    toolWindow()?.hide()
  }

  /** Switches to [InspectorMode.DOCKED]: closes the detached window and shows the tool window with the same state. */
  fun dock() {
    mode = InspectorMode.DOCKED
    detached.close()
    toolWindow()?.show()
    // the tool window's frame stays blocked while a modal dialog is open: show the inspector over that dialog
    val host = select(windowOf(null))
    if (host is ResultsHost.OwnedWindow) showOwned(host.owner)
  }

  /** The tool window was shown in [InspectorMode.DETACHED] (stripe button, shortcut): show the detached window instead. */
  fun toolWindowShown(toolWindow: ToolWindow) {
    if (mode != InspectorMode.DETACHED) return
    // not from inside the tool window's show
    later {
      if (mode == InspectorMode.DETACHED) {
        toolWindow.hide()
        showResults(null)
      }
    }
  }

  fun ensureWatching() {
    if (watching) return
    watching = true
    // any() modality: state changes must reach us while a modal dialog is open
    coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      var previous: InspectorState = controller.state.value
      controller.state.collect { state ->
        if (state is InspectorState.Inspecting && previous is InspectorState.Picking) {
          when (val host = select(windowOf(null))) {
            ResultsHost.ToolWindow -> {}
            is ResultsHost.OwnedWindow, is ResultsHost.DetachedWindow -> show(host)
          }
        }
        previous = state
      }
    }
  }

  private fun later(action: () -> Unit) {
    // any() modality: the detached window is usable while a modal dialog is open
    coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) { action() }
  }

  /** The "dock" button of the detached window: the window must not close while it dispatches the click. */
  private fun dockLater() = later(::dock)

  private fun select(window: Window?): ResultsHost<Window> = ResultsHostSelection.select(window, mode, modalExclusionSupported)

  private fun show(host: ResultsHost<Window>) {
    when (host) {
      ResultsHost.ToolWindow -> toolWindow()?.show()
      is ResultsHost.OwnedWindow -> showOwned(host.owner)
      is ResultsHost.DetachedWindow -> detached.show(host.owner)
    }
  }

  private fun toolWindow(): ToolWindow? = ToolWindowManager.getInstance(project).getToolWindow(ICON_INSPECTOR_TOOLWINDOW_ID)

  private fun windowOf(component: Component?): Window? =
    component?.let { ComponentUtil.getWindow(it) } ?: KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow

  private fun showOwned(owner: Window) {
    val existing = ownedWindows[owner]
    if (existing != null && !existing.isDisposed) {
      existing.window?.toFront()
      return
    }
    val dialog = IconInspectorDialog(owner, controller)
    ownedWindows[owner] = dialog
    closeWithOwner(dialog, owner) { closeIfOpen(dialog) }
    Disposer.register(dialog.disposable) {
      if (ownedWindows[owner] === dialog) ownedWindows.remove(owner)
    }
    dialog.show()
  }

  /** The user closed the detached window: back to [InspectorMode.DOCKED], without showing the tool window. */
  private fun onDetachedClosedByUser(host: DetachedInspectorHost) {
    if (detached.forget(host)) mode = InspectorMode.DOCKED
  }

  /** The modal dialog [owner] of the detached window closes: move the window to the next modal dialog, or out of it. */
  private fun onDetachedOwnerGone(owner: Window) {
    if (disposed || project.isDisposed) return
    detached.ownerGone(owner, ResultsHostSelection.modalOwner(owner.owner, { it.owner }, ResultsHostSelection::isShowingModalDialog))
  }

  /** Calls [onOwnerClosed] when the modal dialog [owner] of [dialog] closes, and closes [dialog] when the project closes. */
  private fun closeWithOwner(dialog: IconInspectorDialog, owner: Window, onOwnerClosed: () -> Unit) {
    var dialogDisposed = false
    val ownerClosed = { if (!dialogDisposed) onOwnerClosed() }
    val closeWithOwner = object : WindowAdapter() {
      override fun windowClosed(e: WindowEvent) = ownerClosed()
    }
    owner.addWindowListener(closeWithOwner)
    // closes the window with the project (this service); dropped as soon as the window is gone, so nothing accumulates
    val closeWithProject = Disposable { if (!dialogDisposed) closeIfOpen(dialog) }
    Disposer.register(this, closeWithProject)
    Disposer.register(dialog.disposable) {
      dialogDisposed = true
      owner.removeWindowListener(closeWithOwner)
      Disposer.dispose(closeWithProject)
    }
    // a DialogWrapper owner is closed via its disposable before AWT reports windowClosed
    DialogWrapper.findInstance(owner)?.disposable?.let { ownerDisposable ->
      Disposer.register(ownerDisposable, Disposable { ownerClosed() })
    }
  }

  private fun closeIfOpen(dialog: IconInspectorDialog) {
    if (!dialog.isDisposed) dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
  }

  override fun dispose() {
    disposed = true
    detached.close()
    ownedWindows.clear()
  }

  /** Opens and closes the hosts of the detached window. */
  private inner class DetachedHosts : DetachedWindowSlot.Hosts<Window, DetachedInspectorHost> {
    override fun open(owner: Window?, bounds: Rectangle?): DetachedInspectorHost =
      if (owner == null) openFrame(bounds) else openOwned(owner, bounds)

    override fun close(host: DetachedInspectorHost) = host.closeHost()

    override fun bounds(host: DetachedInspectorHost): Rectangle? = host.hostWindow()?.takeIf { it.isShowing }?.bounds

    override fun toFront(host: DetachedInspectorHost) {
      host.hostWindow()?.toFront()
    }

    private fun openFrame(bounds: Rectangle?): IconInspectorFrame {
      val frame = IconInspectorFrame(project, controller, ::dockLater, modalExclusionSupported, ::onDetachedClosedByUser)
      Disposer.register(this@IconInspectorWindows, frame)
      // the frame closes itself with the project (FrameWrapper): keep the mode, only drop the frame
      Disposer.register(frame) { detached.forget(frame) }
      frame.open(bounds)
      return frame
    }

    /** The fallback without modal exclusion: the detached window as a window owned by the modal dialog [owner]. */
    private fun openOwned(owner: Window, bounds: Rectangle?): IconInspectorDialog {
      val dialog = IconInspectorDialog(owner, controller, onDock = ::dockLater, onUserClose = ::onDetachedClosedByUser)
      closeWithOwner(dialog, owner) { onDetachedOwnerGone(owner) }
      // AWT can dispose the dialog with its owner before the owner reports closing: also move the inspector then
      Disposer.register(dialog.disposable) { onDetachedOwnerGone(owner) }
      dialog.show()
      if (bounds != null) dialog.window?.bounds = bounds
      return dialog
    }
  }

  private companion object {
    const val DETACHED_PROPERTY = "IconInspector.Detached"
  }
}

/**
 * The inspector in its own window, owned by a modal dialog. A [DialogWrapper] with the modal dialog as parent and
 * `isModal = false`: AWT does not block windows owned by a modal dialog, so it stays usable while the dialog is open; it is
 * the platform's dialog chrome (title bar, theme, Esc to close), and its owner closing disposes it. No buttons: it is a
 * tool window replacement.
 *
 * In [InspectorMode.DOCKED], it is the window for picks in that dialog and remembers its bounds through
 * [getDimensionServiceKey]. In [InspectorMode.DETACHED] ([onDock] is set), it is the detached window moved into the dialog
 * (see [DetachedWindowSlot]): its bounds come from the previous host, and [onUserClose] runs when the user closes it.
 */
internal class IconInspectorDialog(
  owner: Window,
  private val controller: IconInspectorController,
  private val onDock: (() -> Unit)? = null,
  private val onUserClose: ((IconInspectorDialog) -> Unit)? = null,
) : DialogWrapper(owner, true), DetachedInspectorHost {
  init {
    isModal = false
    title = DevkitIconsInspectorBundle.message("dialogs.window.title")
    init()
  }

  override fun createCenterPanel(): JComponent = createInspectorComponent(controller, onDock)

  override fun createSouthPanel(): JComponent? = null

  override fun createActions(): Array<javax.swing.Action> = emptyArray()

  override fun getDimensionServiceKey(): String? = if (onDock == null) "IconInspector.DialogWindow" else null

  /** Esc or the close button. */
  override fun doCancelAction() {
    onUserClose?.invoke(this)
    super.doCancelAction()
  }

  override fun hostWindow(): Window? = if (isDisposed) null else window

  override fun closeHost() {
    if (!isDisposed) close(CANCEL_EXIT_CODE)
  }
}
