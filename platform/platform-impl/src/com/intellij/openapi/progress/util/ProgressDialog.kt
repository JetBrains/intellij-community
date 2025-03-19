// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(FlowPreview::class)

package com.intellij.openapi.progress.util

import com.intellij.concurrency.ContextAwareRunnable
import com.intellij.ide.ui.laf.darcula.ui.DarculaProgressBarUI
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapperPeer
import com.intellij.openapi.ui.impl.GlassPaneDialogWrapperPeer
import com.intellij.openapi.ui.impl.GlassPaneDialogWrapperPeer.GlasspanePeerUnavailableException
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.WindowManager
import com.intellij.platform.util.coroutines.flow.throttle
import com.intellij.ui.PopupBorder
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Window
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import java.lang.Runnable
import javax.swing.*
import javax.swing.border.Border

@Internal
class ProgressDialog(
  private val progressWindow: ProgressWindow,
  private val shouldShowBackground: Boolean,
  cancelText: @Nls String?,
  private val parentWindow: Window?,
) : Disposable {
  companion object {
    internal const val UPDATE_INTERVAL: Int = 50 // msec. 20 frames per second.
  }

  private var lastTimeDrawn: Long = -1
  private val updateRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val cancelButtonEnabledRequests = MutableSharedFlow<Boolean>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private var wasShown = false
  private val startMillis = System.currentTimeMillis()

  private val ui = ProgressDialogUI()

  private val repaintRunnable = Runnable(::doRepaint)

  private var repaintedFlag = true // guarded by this
  private var popup: DialogWrapper? = null

  @Suppress("SSBasedInspection")
  private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  init {
    ui.progressBar.isIndeterminate = progressWindow.isIndeterminate
    val cancelButton = ui.cancelButton
    if (cancelText != null) {
      cancelButton.text = cancelText
    }
    if (progressWindow.myShouldShowCancel) {
      cancelButton.addActionListener {
        progressWindow.cancel()
      }
      val cancelFunction = ActionListener {
        if (cancelButton.isEnabled) {
          progressWindow.cancel()
        }
      }
      cancelButton.registerKeyboardAction(
        cancelFunction,
        KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
      )
    }
    else {
      cancelButton.isVisible = false
    }
    val backgroundButton = ui.backgroundButton
    if (shouldShowBackground) {
      backgroundButton.addActionListener {
        progressWindow.background()
      }
    }
    else {
      backgroundButton.isVisible = false
    }

    coroutineScope.launch {
      updateRequests
        .throttle(500)
        .collectLatest {
          withContext(Dispatchers.EDT + progressWindow.modalityState.asContextElement()) {
            if (!repaintedFlag) {
              return@withContext
            }

            if (System.currentTimeMillis() > lastTimeDrawn + UPDATE_INTERVAL) {
              repaintedFlag = false
              doRepaint()
            }
            else {
              scheduleUpdate()
            }
          }
        }
    }

    coroutineScope.launch {
      val anyModalityContext = Dispatchers.EDT + ModalityState.any().asContextElement()
      cancelButtonEnabledRequests
        .debounce(500)
        .collectLatest { isEnabled ->
          withContext(anyModalityContext) {
            ui.cancelButton.isEnabled = isEnabled
          }
        }
    }
  }

  private fun doRepaint() {
    ui.updateTitle(progressWindow.title)
    ui.updateProgress(
      text = progressWindow.text,
      details = progressWindow.text2,
      fraction = if (progressWindow.isIndeterminate) null else progressWindow.fraction,
    )
    val progressBar = ui.progressBar
    if (progressBar.isShowing && progressBar.isIndeterminate && isWriteActionProgress()) {
      val progressBarUI = progressBar.ui
      if (progressBarUI is DarculaProgressBarUI) {
        progressBarUI.updateIndeterminateAnimationIndex(startMillis)
      }
    }
    lastTimeDrawn = System.currentTimeMillis()
    synchronized(this@ProgressDialog) {
      repaintedFlag = true
    }
  }

  override fun dispose() {
    coroutineScope.cancel()

    Disposer.dispose(ui)
  }

  val panel: JPanel get() = ui.panel

  fun getRepaintRunnable(): Runnable = repaintRunnable

  fun getPopup(): DialogWrapper? = popup

  fun cancel() {
    enableCancelButtonIfNeeded(false)
  }

  fun enableCancelButtonIfNeeded(enable: Boolean) {
    if (progressWindow.myShouldShowCancel) {
      check(cancelButtonEnabledRequests.tryEmit(enable))
    }
  }

  fun scheduleUpdate() {
    check(updateRequests.tryEmit(Unit))
  }

  @Synchronized
  fun background() {
    if (shouldShowBackground) {
      ui.backgroundButton.isEnabled = false
    }

    hide()
  }

  fun hide() {
    ApplicationManager.getApplication().invokeLater(ContextAwareRunnable(this::hideImmediately), ModalityState.any())
  }

  fun hideImmediately() {
    if (popup != null) {
      popup!!.close(DialogWrapper.CANCEL_EXIT_CODE)
      popup = null
    }
  }

  fun show() {
    if (wasShown) {
      return
    }
    wasShown = true

    if (ApplicationManager.getApplication().isHeadlessEnvironment || parentWindow == null) {
      return
    }
    if (popup != null) {
      popup!!.close(DialogWrapper.CANCEL_EXIT_CODE)
    }

    val popup = createDialog(parentWindow)
    this.popup = popup

    popup.show()

    // 'Light' popup is shown in glass pane, glass pane is 'activating' (becomes visible) in 'invokeLater' call
    // (see IdeGlassPaneImp.addImpl), requesting focus to cancel button until that time has no effect, as it's not showing.
    SwingUtilities.invokeLater {
      if (this.popup != null && !this.popup!!.isDisposed) {
        val window = SwingUtilities.getWindowAncestor(ui.cancelButton)
        if (window != null) {
          val originalFocusOwner = window.mostRecentFocusOwner
          if (originalFocusOwner != null) {
            Disposer.register(this.popup!!.disposable) { originalFocusOwner.requestFocusInWindow() }
          }
        }
        ui.cancelButton.requestFocusInWindow()
        doRepaint()
      }
    }
  }

  private fun isWriteActionProgress(): Boolean = progressWindow is PotemkinProgress

  private fun createDialog(window: Window): DialogWrapper {
    if (Registry.`is`("ide.modal.progress.wrapper.refactoring")) {
      return createDialogWrapper(
        panel = panel,
        cancelAction = {
          if (progressWindow.myShouldShowCancel) {
            progressWindow.cancel()
          }
        },
        window = window,
        writeAction = isWriteActionProgress(),
        project = progressWindow.myProject,
      )
    }
    return createDialogPrevious(window).also {
      setupProgressDialog(it, isWriteActionProgress())
    }
  }

  private fun createDialogPrevious(window: Window): MyDialogWrapper {
    if (isWriteActionProgress()) {
      if (window.isShowing) {
        return object : MyDialogWrapper(window) {
          override fun useLightPopup(): Boolean = false
        }
      }
      return object : MyDialogWrapper(progressWindow.myProject) {
        override fun useLightPopup(): Boolean = false
      }
    }
    // GTW-1384 - If the parent window is JOptionPane.getRootFrame() then invoke DialogWrapper(Component) instead of DialogWrapper(Project)
    // because otherwise the ToolbarUtil.setTransparentTitleBar(...) is invoked.
    // AFAIU: It should only affect progresses that are shown without any parent window (like the Gateway started from IDE)
    if (window.isShowing || window == JOptionPane.getRootFrame()) {
      return MyDialogWrapper(window)
    }
    return MyDialogWrapper(progressWindow.myProject)
  }

  private open inner class MyDialogWrapper : DialogWrapper {
    constructor(project: Project?) : super(project, false) {
      init()
    }

    constructor(parent: Component) : super(parent, false) {
      init()
    }

    final override fun doCancelAction() {
      if (progressWindow.myShouldShowCancel) {
        progressWindow.cancel()
      }
    }

    final override fun createPeer(parent: Component, canBeParent: Boolean): DialogWrapperPeer {
      return if (useLightPopup() && areLightPopupsEnabled()) {
        try {
          GlassPaneDialogWrapperPeer(this, parent)
        }
        catch (e: GlasspanePeerUnavailableException) {
          super.createPeer(parent, canBeParent)
        }
      }
      else {
        super.createPeer(parent, canBeParent)
      }
    }

    final override fun createPeer(owner: Window, canBeParent: Boolean, applicationModalIfPossible: Boolean): DialogWrapperPeer {
      return if (useLightPopup() && areLightPopupsEnabled()) {
        try {
          GlassPaneDialogWrapperPeer(this)
        }
        catch (e: GlasspanePeerUnavailableException) {
          super.createPeer(WindowManager.getInstance().suggestParentWindow(progressWindow.myProject), canBeParent,
                           applicationModalIfPossible)
        }
      }
      else {
        super.createPeer(WindowManager.getInstance().suggestParentWindow(progressWindow.myProject), canBeParent,
                         applicationModalIfPossible)
      }
    }

    protected open fun useLightPopup(): Boolean = true

    final override fun createPeer(project: Project?, canBeParent: Boolean): DialogWrapperPeer {
      return try {
        GlassPaneDialogWrapperPeer(project, this)
      }
      catch (e: GlasspanePeerUnavailableException) {
        super.createPeer(project, canBeParent)
      }
    }

    final override fun init() {
      super.init()
      setUndecorated(true)
      rootPane.windowDecorationStyle = JRootPane.NONE
      panel.border = PopupBorder.Factory.create(true, true)
    }

    final override fun isProgressDialog(): Boolean = true

    final override fun createCenterPanel(): JComponent = panel

    final override fun createSouthPanel(): JComponent? = null

    final override fun createContentPaneBorder(): Border? = null
  }
}