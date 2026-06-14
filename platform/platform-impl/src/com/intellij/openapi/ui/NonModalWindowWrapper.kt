// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(FlowPreview::class)

package com.intellij.openapi.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.WindowStateService
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy
import com.intellij.openapi.wm.impl.IdeFrameDecorator
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomFrameDialogContent
import com.intellij.ui.ComponentUtil
import com.intellij.ui.FullScreenSupport
import com.intellij.ui.ToolbarService
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.awt.AWTEvent
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Frame
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JRootPane
import javax.swing.KeyStroke
import javax.swing.RootPaneContainer
import kotlin.time.Duration.Companion.seconds

/**
 * Reusable base class for non-modal IDE windows that support two display modes,
 * toggled via a pin button in the window toolbar:
 *
 * - **Float mode** (pin ON): a non-modal [JDialog] owned by the IDE frame. Stays above the IDE
 *   via Java's window-ownership chain — no `isAlwaysOnTop` required, works on Linux, and modal
 *   dialogs appear above it naturally. No OS minimize button.
 * - **Window mode** (pin OFF): an independent [JFrame]. Has an OS minimize button; can go behind
 *   the IDE when the IDE is clicked.
 *
 * Switching modes disposes the old AWT window and creates a new one of the other type; the shared
 * content component is reparented between windows.
 *
 * **Usage pattern:**
 * ```kotlin
 * class MyNonModalWindow(project: Project) : NonModalWindowWrapper(project, "my.float.key") {
 *   private val content: JPanel
 *
 *   init {
 *     content = buildContent(createModeAction())   // createModeAction() before initWindow()
 *     initWindow(content, JBUI.size(800, 600), JBUI.size(800, 600))
 *     // add extra panels to content AFTER initWindow() if they need activeWindow
 *   }
 *
 *   override fun getWindowTitle() = "My Window"
 *   override fun canClose(e: AWTEvent): Boolean = !hasUnsavedChanges()
 * }
 * ```
 */
@ApiStatus.Internal
abstract class NonModalWindowWrapper(
  protected val project: Project,
  private val floatModeKey: String,
  private val dimensionKey: String? = null,
) : Disposable, UiDataProvider {

  // ── State ────────────────────────────────────────────────────────────────────

  var isDisposed: Boolean = false
    private set

  protected val frameDisposable: Disposable = Disposer.newDisposable(javaClass.simpleName)

  /** The current AWT window — either a [JFrame] (Window mode) or [JDialog] (Float mode). */
  protected lateinit var activeWindow: Window
    private set

  private lateinit var content: JComponent
  private lateinit var minWindowSize: Dimension
  private var windowListener: WindowAdapter? = null
  private var windowDisposable: Disposable? = null

  protected var isFloat: Boolean
    get() = PropertiesComponent.getInstance().getBoolean(floatModeKey, true)
    set(value) { PropertiesComponent.getInstance().setValue(floatModeKey, value, true) }

  // ── Abstract / open hooks ────────────────────────────────────────────────────

  /** OS title bar text. */
  protected abstract fun getWindowTitle(): String

  /**
   * Returns the string set as the AWT accessible name of the window.
   * Defaults to [getWindowTitle]. Override to return a shorter name
   * (e.g. without the project-name suffix) for accessibility and UI-test locators.
   */
  protected open fun getAccessibleWindowName(): String = getWindowTitle()

  /** Component to focus when the window first becomes visible. */
  protected open fun getPreferredFocusComponent(): JComponent? = null

  /**
   * Called once when the window is made visible for the first time (not when brought to front).
   * Override to hook into the "window shown" lifecycle event.
   */
  protected open fun onShown() {}

  /**
   * Called when the window loses focus to a non-owned window (i.e. a genuine app-switch
   * or unrelated window; NOT triggered when focus moves to an owned child dialog).
   */
  protected open fun onWindowDeactivated() {}

  /**
   * Called when the window gains focus from a non-owned window (genuine app-switch or
   * unrelated window).
   */
  protected open fun onWindowActivated() {}

  /**
   * Called when the user clicks the X (close) button.
   * Default: [close] (full dispose). Override to change behaviour (e.g. hide-only).
   */
  protected open fun onWindowClosing(e: WindowEvent) { close() }

  /**
   * Called by the ESC / Cmd-W close handler. Return `true` to close the window, `false` to
   * consume the key without closing (e.g. to clear a search filter on the first ESC).
   */
  protected open fun canClose(e: AWTEvent): Boolean = true

  /** Override to install additional keyboard shortcuts on the root pane. */
  protected open fun installAdditionalShortcuts(rootPane: JRootPane) {}

  // ── Initialisation ───────────────────────────────────────────────────────────

  /**
   * Returns the pin / mode-toggle action for injection into the window toolbar.
   *
   * **Call this before [initWindow]** so the action can be passed to any editor or component
   * constructor that needs it. (The action's `setSelected` callback is wired to
   * [switchWindowMode], which requires [activeWindow] to be initialised — so the action must
   * not be activated before [initWindow] is called.)
   */
  fun createModeAction(): ToggleAction = object : ToggleAction(
    IdeBundle.messagePointer("action.ToggleAction.text.pin.window"),
    IdeBundle.messagePointer("action.ToggleAction.description.pin.window"),
    AllIcons.General.Pin_tab,
  ) {
    override fun isSelected(e: AnActionEvent): Boolean = isFloat
    override fun setSelected(e: AnActionEvent, state: Boolean) {
      isFloat = state
      switchWindowMode(state)
    }
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    override fun isDumbAware(): Boolean = true
  }

  /**
   * Finishes window initialisation. **Must be called by the subclass** once [content] is
   * ready and all subclass fields are initialised (because [installWindowListeners] invokes
   * open hooks such as [onWindowActivated]).
   *
   * Any panels that need [activeWindow] (e.g. a button panel that looks up the root pane for the
   * default button) must be added to [content] **after** calling this method.
   */
  protected fun initWindow(content: JComponent, minSize: Dimension, initialSize: Dimension) {
    this.content = content
    this.minWindowSize = minSize
    activeWindow = createAwtWindow(isFloat, content, minSize, initialSize)
    loadAndRegisterWindowState(activeWindow)
    installWindowListeners()
    installToolkitListener()
  }

  private fun loadAndRegisterWindowState(window: Window) {
    val key = dimensionKey ?: return
    val state = WindowStateService.getInstance(project).getState(key, window)
    state?.applyTo(window)
  }

  // ── Window creation and mode switching ──────────────────────────────────────

  private fun createAwtWindow(
    float: Boolean,
    content: JComponent,
    minSize: Dimension,
    size: Dimension,
  ): Window {
    val title = getWindowTitle()
    return if (float) {
      FloatDialog(getIdeJFrame(), title).also { dialog ->
        dialog.contentPane.layout = BorderLayout()
        val dialogContent = if (IdeFrameDecorator.isCustomDecorationActive())
          CustomFrameDialogContent.getCustomContentHolder(dialog, content, false) else content
        dialog.contentPane.add(dialogContent)
        dialog.minimumSize = minSize
        dialog.size = size
        dialog.setLocationRelativeTo(dialog.owner)
        dialog.glassPane = IdeGlassPaneImpl(dialog.rootPane)
        ComponentUtil.decorateWindowHeader(dialog.rootPane)
        dialog.accessibleContext.accessibleName = getAccessibleWindowName()
        val wd = Disposer.newDisposable(frameDisposable)
        windowDisposable = wd
        ToolbarService.getInstance().setTransparentTitleBar(
          window = dialog,
          rootPane = dialog.rootPane,
          onDispose = { runnable -> Disposer.register(wd) { runnable.run() } },
        )
      }
    }
    else {
      WindowFrame().also { frame ->
        frame.contentPane.layout = BorderLayout()
        val frameContent = if (IdeFrameDecorator.isCustomDecorationActive())
          CustomFrameDialogContent.getCustomContentHolder(frame, content, false) else content
        frame.contentPane.add(frameContent)
        frame.title = title
        frame.minimumSize = minSize
        frame.size = size
        frame.setLocationRelativeTo(getIdeJFrame())
        frame.glassPane = IdeGlassPaneImpl(frame.rootPane)
        ComponentUtil.decorateWindowHeader(frame.rootPane)
        frame.accessibleContext.accessibleName = getAccessibleWindowName()
        val wd = Disposer.newDisposable(frameDisposable)
        windowDisposable = wd
        ToolbarService.getInstance().setTransparentTitleBar(
          window = frame,
          rootPane = frame.rootPane,
          handlerProvider = { FullScreenSupport.NEW.apply("com.intellij.ui.mac.MacFullScreenSupport") },
          onDispose = { runnable -> Disposer.register(wd) { runnable.run() } },
        )
      }
    }
  }

  private inner class FloatDialog(owner: JFrame?, title: String) : JDialog(owner, title, false), UiDataProvider {
    init {
      defaultCloseOperation = DO_NOTHING_ON_CLOSE
      background = UIUtil.getPanelBackground()
      focusTraversalPolicy = IdeFocusTraversalPolicy()
      UIUtil.markAsPossibleOwner(this)
    }

    override fun uiDataSnapshot(sink: DataSink): Unit = this@NonModalWindowWrapper.uiDataSnapshot(sink)
  }

  private inner class WindowFrame : JFrame(), UiDataProvider {
    init {
      defaultCloseOperation = DO_NOTHING_ON_CLOSE
      background = UIUtil.getPanelBackground()
      focusTraversalPolicy = IdeFocusTraversalPolicy()
    }

    override fun uiDataSnapshot(sink: DataSink): Unit = this@NonModalWindowWrapper.uiDataSnapshot(sink)
  }

  /**
   * Switches between Float ([JDialog]) and Window ([JFrame]) mode.
   * The [content] component is reparented and window bounds are transferred.
   */
  private fun switchWindowMode(toFloat: Boolean) {
    val bounds = activeWindow.bounds
    val wasVisible = activeWindow.isVisible
    val savedDefaultButton = (activeWindow as RootPaneContainer).rootPane.defaultButton
    windowListener?.let { activeWindow.removeWindowListener(it) }
    windowListener = null
    content.parent?.remove(content)
    disposeWindow(activeWindow)
    windowDisposable?.let { Disposer.dispose(it) }
    windowDisposable = null
    activeWindow = createAwtWindow(toFloat, content, minWindowSize, bounds.size)
    installWindowListeners()
    savedDefaultButton?.let { (activeWindow as RootPaneContainer).rootPane.defaultButton = it }
    activeWindow.bounds = bounds
    dimensionKey?.let { WindowStateService.getInstance(project).getState(it, activeWindow) }
    if (wasVisible) {
      activeWindow.isVisible = true
      activeWindow.toFront()
      activeWindow.requestFocus()
    }
  }

  // ── Window listeners ─────────────────────────────────────────────────────────

  private fun installWindowListeners() {
    val rootPane = (activeWindow as RootPaneContainer).rootPane

    val adapter = object : WindowAdapter() {
      private var savedDefaultButton: javax.swing.JButton? = null

      override fun windowDeactivated(e: WindowEvent) {
        if (rootPane.defaultButton != null) {
          savedDefaultButton = rootPane.defaultButton
          rootPane.defaultButton = null
          activeWindow.repaint()
        }
      }

      override fun windowActivated(e: WindowEvent) {
        savedDefaultButton?.let { btn ->
          rootPane.defaultButton = btn
          savedDefaultButton = null
          activeWindow.repaint()
        }
      }

      override fun windowClosing(e: WindowEvent) {
        onWindowClosing(e)
      }
    }
    windowListener = adapter
    activeWindow.addWindowListener(adapter)

    // ESC / Cmd-W: ask the subclass whether it is safe to close.
    val closeHandler = ActionListener { e ->
      val current = EventQueue.getCurrentEvent()
      if (canClose(current as? KeyEvent ?: e)) close()
    }
    rootPane.registerKeyboardAction(
      closeHandler, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW)
    ActionUtil.registerForEveryKeyboardShortcut(rootPane, closeHandler, CommonShortcuts.getCloseActiveWindow())

    installAdditionalShortcuts(rootPane)
  }

  private fun installToolkitListener() {
    content.launchOnShow("Non-modal settings AWT event listener") {
      // The default modality prevents us from processing nested modal dialogs,
      // but we absolutely do want to process them here.
      withContext(ModalityState.any().asContextElement()) {
        val globallyActiveWindow = MutableStateFlow<Window?>(null)

        launch {
          val toolkit = Toolkit.getDefaultToolkit()
          val listener = AWTEventListener { event ->
            when (event.id) {
              WindowEvent.WINDOW_ACTIVATED -> {
                globallyActiveWindow.value = event.source as Window
              }
              WindowEvent.WINDOW_DEACTIVATED -> {
                // Guesswork: if the app lost focus for good, this will be the last value for a while.
                // Otherwise, a WINDOW_ACTIVATED event is expected soon enough, which will overwrite the value.
                // That's why debouncing is absolutely needed.
                // But it's the only way to make it work on Wayland, as it doesn't provide WindowEvent.opposite.
                globallyActiveWindow.value = null
              }
            }
          }
          toolkit.addAWTEventListener(listener, AWTEvent.WINDOW_EVENT_MASK)
          try {
            // Initial value, set it now instead of the flow constructor to make sure the listener is already listening.
            globallyActiveWindow.value = if (activeWindow.isActive) activeWindow else null
            awaitCancellation()
          }
          finally {
            toolkit.removeAWTEventListener(listener)
          }
        }

        globallyActiveWindow
          .debounce { globallyActiveWindow ->
            // A "deactivate" event may be followed by an "activate" event very soon, so we need to debounce.
            // "Activate" events don't need debouncing, and it would even be harmful,
            // as a click inside can both activate the window and do something else immediately,
            // and by that time we better have the correct status already.
            if (globallyActiveWindow == null) 1.seconds else 0.seconds
          }
          .map { globallyActiveWindow ->
            LOG.debug { "Active window changed: $globallyActiveWindow" }
            // Don't trigger "deactivate" when focus moves to an owned child dialog (e.g. a file chooser opened
            // by a configurable): changes made there are intentional and should not be rolled back.
            // Don't trigger "activate" when returning from an owned child dialog.
            // All other cases — including null (another OS app) and unrelated windows — are genuine app-switches.
            globallyActiveWindow != null && globallyActiveWindow.isSameOrOwnedBy(activeWindow)
          }
          .distinctUntilChanged()
          .collect { isOurWindowActive ->
            LOG.debug { "Active status changed: $isOurWindowActive" }
            if (isOurWindowActive) {
              onWindowActivated()
            }
            else {
              onWindowDeactivated()
            }
          }
      }
    }
  }

  // ── Utilities ────────────────────────────────────────────────────────────────

  /** Returns the IDE's JFrame for [project]; used as the [JDialog] owner in Float mode. */
  protected fun getIdeJFrame(): JFrame? =
    ComponentUtil.getWindow(WindowManager.getInstance().getIdeFrame(project)?.component) as? JFrame

  // ── Public API ───────────────────────────────────────────────────────────────

  /**
   * Default data exposed by every non-modal window. Always includes [CommonDataKeys.PROJECT]
   * so actions invoked over this window (e.g. Search Everywhere, action search) resolve the
   * owning project correctly — regardless of mode (Float [JDialog] / Window [JFrame]) and
   * regardless of whether an [com.intellij.openapi.wm.IdeFrame] is reachable via AWT ownership.
   *
   * Subclasses overriding this method MUST call `super.uiDataSnapshot(sink)` to keep the
   * project available
   */
  override fun uiDataSnapshot(sink: DataSink) {
    sink[CommonDataKeys.PROJECT] = project
  }

  /** Shows the window. If already visible, brings it to front. Un-iconifies a minimised [JFrame]. */
  fun show() {
    if (activeWindow.isVisible) {
      if (activeWindow is Frame && ((activeWindow as Frame).extendedState and Frame.ICONIFIED) != 0) {
        (activeWindow as Frame).extendedState = (activeWindow as Frame).extendedState and Frame.ICONIFIED.inv()
      }
      activeWindow.toFront()
      activeWindow.requestFocus()
      return
    }
    activeWindow.isVisible = true
    getPreferredFocusComponent()?.requestFocusInWindow()
    onShown()
  }

  /** Disposes this window and all associated resources. */
  fun close() {
    if (!isDisposed) Disposer.dispose(this)
  }

  override fun dispose() {
    isDisposed = true
    windowListener?.let { activeWindow.removeWindowListener(it) }
    windowListener = null
    Disposer.dispose(frameDisposable)
    disposeWindow(activeWindow)
  }

  private fun disposeWindow(window: Window) {
    window.dispose()
    val rootPane = (window as? RootPaneContainer)?.rootPane
    rootPane?.resetKeyboardActions()
    DialogWrapper.cleanupRootPane(rootPane)
    DialogWrapper.cleanupWindowListeners(window)
  }
}

/** Returns `true` if this window's ownership (including the window itself) chain passes through [ancestor]. */
private fun Window.isSameOrOwnedBy(ancestor: Window): Boolean {
  var w: Window? = this
  while (w != null) {
    if (w === ancestor) return true
    w = w.owner
  }
  return false
}

private val LOG = logger<NonModalWindowWrapper>()
