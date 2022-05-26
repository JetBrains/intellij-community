// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util

import com.intellij.ide.ui.laf.darcula.ui.DarculaProgressBarUI
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.impl.ProgressState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapperPeer
import com.intellij.openapi.ui.impl.DialogWrapperPeerImpl
import com.intellij.openapi.ui.impl.GlassPaneDialogWrapperPeer
import com.intellij.openapi.ui.impl.GlassPaneDialogWrapperPeer.GlasspanePeerUnavailableException
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.PopupBorder
import com.intellij.util.Alarm
import com.intellij.util.SingleAlarm
import com.intellij.util.ui.EdtInvocationManager
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Window
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.border.Border

class ProgressDialog(private val myProgressWindow: ProgressWindow,
                     private val myShouldShowBackground: Boolean,
                     cancelText: @Nls String?,
                     private val myParentWindow: Window?) : Disposable {
  companion object {
    const val UPDATE_INTERVAL = 50 //msec. 20 frames per second.
  }

  private var myLastTimeDrawn: Long = -1
  private val myUpdateAlarm = SingleAlarm(this::update, 500, this)
  private var myWasShown = false
  private val myStartMillis = System.currentTimeMillis()

  private val ui = ProgressDialogUI()

  private val myRepaintRunnable = Runnable {
    ui.updateTitle(myProgressWindow.title)
    ui.updateProgress(ProgressState(
      text = myProgressWindow.text,
      details = myProgressWindow.text2,
      fraction = if (myProgressWindow.isIndeterminate) -1.0 else myProgressWindow.fraction,
    ))
    val progressBar = ui.progressBar
    if (progressBar.isShowing && progressBar.isIndeterminate && isWriteActionProgress()) {
      val progressBarUI = progressBar.ui
      if (progressBarUI is DarculaProgressBarUI) {
        progressBarUI.updateIndeterminateAnimationIndex(myStartMillis)
      }
    }
    myLastTimeDrawn = System.currentTimeMillis()
    synchronized(this@ProgressDialog) {
      myRepaintedFlag = true
    }
  }

  private var myRepaintedFlag = true // guarded by this
  private var myPopup: DialogWrapper? = null
  private val myDisableCancelAlarm = SingleAlarm(
    this::setCancelButtonDisabledInEDT, 500, null, Alarm.ThreadToUse.SWING_THREAD, ModalityState.any()
  )
  private val myEnableCancelAlarm = SingleAlarm(
    this::setCancelButtonEnabledInEDT, 500, null, Alarm.ThreadToUse.SWING_THREAD, ModalityState.any()
  )

  init {
    ui.progressBar.isIndeterminate = myProgressWindow.isIndeterminate
    val cancelButton = ui.cancelButton
    if (cancelText != null) {
      cancelButton.text = cancelText
    }
    if (myProgressWindow.myShouldShowCancel) {
      cancelButton.addActionListener {
        myProgressWindow.cancel()
      }
      val cancelFunction = ActionListener {
        if (cancelButton.isEnabled) {
          myProgressWindow.cancel()
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
    if (myShouldShowBackground) {
      backgroundButton.addActionListener {
        myProgressWindow.background()
      }
    }
    else {
      backgroundButton.isVisible = false
    }
  }

  override fun dispose() {
    Disposer.dispose(ui)
    myEnableCancelAlarm.cancelAllRequests()
    myDisableCancelAlarm.cancelAllRequests()
  }

  val panel: JPanel get() = ui.panel

  fun getRepaintRunnable(): Runnable = myRepaintRunnable

  fun getPopup(): DialogWrapper? = myPopup

  fun cancel() {
    enableCancelButtonIfNeeded(false)
  }

  private fun setCancelButtonEnabledInEDT() {
    ui.cancelButton.isEnabled = true
  }

  private fun setCancelButtonDisabledInEDT() {
    ui.cancelButton.isEnabled = false
  }

  fun enableCancelButtonIfNeeded(enable: Boolean) {
    if (myProgressWindow.myShouldShowCancel && !myUpdateAlarm.isDisposed) {
      (if (enable) myEnableCancelAlarm else myDisableCancelAlarm).request()
    }
  }

  @Synchronized
  fun update() {
    if (myRepaintedFlag) {
      if (System.currentTimeMillis() > myLastTimeDrawn + UPDATE_INTERVAL) {
        myRepaintedFlag = false
        EdtInvocationManager.invokeLaterIfNeeded(myRepaintRunnable)
      }
      else {
        // later to avoid concurrent dispose/addRequest
        if (!myUpdateAlarm.isDisposed && myUpdateAlarm.isEmpty) {
          EdtInvocationManager.invokeLaterIfNeeded {
            if (!myUpdateAlarm.isDisposed) {
              myUpdateAlarm.request(myProgressWindow.modalityState)
            }
          }
        }
      }
    }
  }

  @Synchronized
  fun background() {
    if (myShouldShowBackground) {
      ui.backgroundButton.isEnabled = false
    }

    hide()
  }

  fun hide() {
    ApplicationManager.getApplication().invokeLater(this::hideImmediately, ModalityState.any())
  }

  fun hideImmediately() {
    if (myPopup != null) {
      myPopup!!.close(DialogWrapper.CANCEL_EXIT_CODE)
      myPopup = null
    }
  }

  fun show() {
    if (myWasShown) {
      return
    }
    myWasShown = true

    if (ApplicationManager.getApplication().isHeadlessEnvironment || myParentWindow == null) {
      return
    }
    if (myPopup != null) {
      myPopup!!.close(DialogWrapper.CANCEL_EXIT_CODE)
    }

    val popup = createDialog(myParentWindow)
    myPopup = popup
    popup.setUndecorated(true)
    if (popup.peer is DialogWrapperPeerImpl) {
      (popup.peer as DialogWrapperPeerImpl).setAutoRequestFocus(false)
      if (isWriteActionProgress()) {
        popup.isModal = false // display the dialog and continue with EDT execution, don't block it forever
      }
    }
    popup.pack()

    Disposer.register(popup.disposable) { myProgressWindow.exitModality() }

    popup.show()

    // 'Light' popup is shown in glass pane, glass pane is 'activating' (becomes visible) in 'invokeLater' call
    // (see IdeGlassPaneImp.addImpl), requesting focus to cancel button until that time has no effect, as it's not showing.
    SwingUtilities.invokeLater {
      if (myPopup != null && !myPopup!!.isDisposed) {
        val window = SwingUtilities.getWindowAncestor(ui.cancelButton)
        if (window != null) {
          val originalFocusOwner = window.mostRecentFocusOwner
          if (originalFocusOwner != null) {
            Disposer.register(myPopup!!.disposable) { originalFocusOwner.requestFocusInWindow() }
          }
        }
        ui.cancelButton.requestFocusInWindow()
        myRepaintRunnable.run()
      }
    }
  }

  private fun isWriteActionProgress(): Boolean {
    return myProgressWindow is PotemkinProgress
  }

  private fun createDialog(window: Window): DialogWrapper {
    if (Registry.`is`("ide.modal.progress.wrapper.refactoring")) {
      return createDialogWrapper(
        panel = panel,
        cancelAction = {
          if (myProgressWindow.myShouldShowCancel) {
            myProgressWindow.cancel()
          }
        },
        window = window,
        lightPopup = !isWriteActionProgress(),
        project = myProgressWindow.myProject,
      )
    }
    if (System.getProperty("vintage.progress") != null || isWriteActionProgress()) {
      if (window.isShowing) {
        return object : MyDialogWrapper(window) {
          override fun useLightPopup(): Boolean {
            return false
          }
        }
      }
      return object : MyDialogWrapper(myProgressWindow.myProject) {
        override fun useLightPopup(): Boolean {
          return false
        }
      }
    }
    if (window.isShowing) {
      return MyDialogWrapper(window)
    }
    return MyDialogWrapper(myProgressWindow.myProject)
  }

  private open inner class MyDialogWrapper : DialogWrapper {

    constructor(project: Project?) : super(project, false) {
      init()
    }

    constructor(parent: Component) : super(parent, false) {
      init()
    }

    override fun doCancelAction() {
      if (myProgressWindow.myShouldShowCancel) {
        myProgressWindow.cancel()
      }
    }

    override fun createPeer(parent: Component, canBeParent: Boolean): DialogWrapperPeer {
      return if (useLightPopup()) {
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

    override fun createPeer(owner: Window, canBeParent: Boolean, applicationModalIfPossible: Boolean): DialogWrapperPeer {
      return if (useLightPopup()) {
        try {
          GlassPaneDialogWrapperPeer(this)
        }
        catch (e: GlasspanePeerUnavailableException) {
          super.createPeer(WindowManager.getInstance().suggestParentWindow(myProgressWindow.myProject), canBeParent,
                           applicationModalIfPossible)
        }
      }
      else {
        super.createPeer(WindowManager.getInstance().suggestParentWindow(myProgressWindow.myProject), canBeParent,
                         applicationModalIfPossible)
      }
    }

    protected open fun useLightPopup(): Boolean {
      return true
    }

    override fun createPeer(project: Project?, canBeParent: Boolean): DialogWrapperPeer {
      return if (System.getProperty("vintage.progress") == null) {
        try {
          GlassPaneDialogWrapperPeer(project, this)
        }
        catch (e: GlasspanePeerUnavailableException) {
          super.createPeer(project, canBeParent)
        }
      }
      else {
        super.createPeer(project, canBeParent)
      }
    }

    override fun init() {
      super.init()
      setUndecorated(true)
      rootPane.windowDecorationStyle = JRootPane.NONE
      panel.border = PopupBorder.Factory.create(true, true)
    }

    override fun isProgressDialog(): Boolean {
      return true
    }

    override fun createCenterPanel(): JComponent {
      return panel
    }

    override fun createSouthPanel(): JComponent? {
      return null
    }

    override fun createContentPaneBorder(): Border? {
      return null
    }
  }
}