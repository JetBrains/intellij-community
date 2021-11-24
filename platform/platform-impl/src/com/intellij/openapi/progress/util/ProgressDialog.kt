// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.util

import com.intellij.CommonBundle
import com.intellij.ide.ui.laf.darcula.ui.DarculaProgressBarUI
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapperDialog
import com.intellij.openapi.ui.DialogWrapperPeer
import com.intellij.openapi.ui.impl.DialogWrapperPeerImpl
import com.intellij.openapi.ui.impl.GlassPaneDialogWrapperPeer
import com.intellij.openapi.ui.impl.GlassPaneDialogWrapperPeer.GlasspanePeerUnavailableException
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.PopupBorder
import com.intellij.ui.TitlePanel
import com.intellij.ui.WindowMoveListener
import com.intellij.ui.components.JBLabel
import com.intellij.ui.scale.JBUIScale
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.Alarm
import com.intellij.util.SingleAlarm
import com.intellij.util.ui.DialogUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Contract
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Dimension
import java.awt.Window
import java.awt.event.ActionEvent
import java.io.File
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

  private val myRepaintRunnable = Runnable {
    val text = myProgressWindow.text
    val fraction = myProgressWindow.fraction
    val text2 = myProgressWindow.text2

    if (myProgressBar.isShowing) {
      myProgressBar.isIndeterminate = myProgressWindow.isIndeterminate
      myProgressBar.value = (fraction * 100).toInt()
      if (myProgressBar.isIndeterminate && isWriteActionProgress() && myProgressBar.ui is DarculaProgressBarUI) {
        (myProgressBar.ui as DarculaProgressBarUI).updateIndeterminateAnimationIndex(myStartMillis)
      }
    }

    myTextLabel.text = fitTextToLabel(text, myTextLabel)
    myText2Label.text = fitTextToLabel(text2, myText2Label)

    myTitlePanel.setText(StringUtil.defaultIfEmpty(myProgressWindow.title, " "))

    myLastTimeDrawn = System.currentTimeMillis()
    synchronized(this@ProgressDialog) {
      myRepaintedFlag = true
    }
  }

  private val myPanel = JPanel()
  private val myTextLabel = JLabel(" ")

  private val myText2Label = JBLabel("")
  private val myCancelButton = JButton()

  private val myBackgroundButton = JButton()
  private val myProgressBar = JProgressBar()

  private var myRepaintedFlag = true // guarded by this

  private val myTitlePanel = TitlePanel()
  private var myPopup: DialogWrapper? = null
  private val myDisableCancelAlarm = SingleAlarm(this::setCancelButtonDisabledInEDT, 500, null, Alarm.ThreadToUse.SWING_THREAD, ModalityState.any())
  private val myEnableCancelAlarm = SingleAlarm(this::setCancelButtonEnabledInEDT, 500, null, Alarm.ThreadToUse.SWING_THREAD, ModalityState.any())

  init {
    setupUI()
    initDialog(cancelText)
  }

  private fun setupUI() {
    myPanel.layout = GridLayoutManager(2, 1, JBUI.emptyInsets(), -1, -1, false, false)

    val panel = JPanel()
    panel.layout = GridLayoutManager(1, 2, JBUI.insets(6, 10, 10, 10), -1, -1, false, false)
    panel.isOpaque = false
    myPanel.add(panel, GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                       GridConstraints.SIZEPOLICY_CAN_GROW or GridConstraints.SIZEPOLICY_CAN_SHRINK,
                                       GridConstraints.SIZEPOLICY_CAN_GROW or GridConstraints.SIZEPOLICY_CAN_SHRINK, null, null, null))

    val innerPanel = JPanel()
    innerPanel.layout = GridLayoutManager(3, 2, JBUI.emptyInsets(), -1, -1, false, false)
    innerPanel.preferredSize = Dimension(if (SystemInfo.isMac) 350 else JBUIScale.scale(450), -1)
    panel.add(innerPanel, GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                          GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW or
                                            GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null))

    innerPanel.add(myTextLabel, GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                (GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW or
                                                  GridConstraints.SIZEPOLICY_WANT_GROW), GridConstraints.SIZEPOLICY_FIXED,
                                                Dimension(0, -1), null, null))

    myText2Label.componentStyle = UIUtil.ComponentStyle.REGULAR
    innerPanel.add(myText2Label, GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_NORTHWEST, GridConstraints.FILL_HORIZONTAL,
                                                 (GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW or
                                                   GridConstraints.SIZEPOLICY_WANT_GROW), GridConstraints.SIZEPOLICY_FIXED,
                                                 Dimension(0, -1), null, null))

    myProgressBar.putClientProperty("html.disable", java.lang.Boolean.FALSE)
    innerPanel.add(myProgressBar, GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                  (GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW or
                                                    GridConstraints.SIZEPOLICY_WANT_GROW), GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                  null))

    innerPanel.add(JLabel(" "),
                   GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null))
    innerPanel.add(JLabel(" "),
                   GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null))

    val buttonPanel = JPanel()
    buttonPanel.layout = GridLayoutManager(2, 1, JBUI.emptyInsets(), -1, -1, false, false)
    panel.add(buttonPanel, GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                           GridConstraints.SIZEPOLICY_CAN_SHRINK, GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                           null))

    myCancelButton.text = CommonBundle.getCancelButtonText()
    DialogUtil.registerMnemonic(myCancelButton, '&')
    buttonPanel.add(myCancelButton, GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                    GridConstraints.SIZEPOLICY_CAN_GROW or GridConstraints.SIZEPOLICY_CAN_SHRINK,
                                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null))

    myBackgroundButton.text = CommonBundle.message("button.background")
    DialogUtil.registerMnemonic(myBackgroundButton, '&')
    buttonPanel.add(myBackgroundButton, GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                        GridConstraints.SIZEPOLICY_CAN_GROW or GridConstraints.SIZEPOLICY_CAN_SHRINK,
                                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null))

    myPanel.add(myTitlePanel, GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                              (GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW or
                                                GridConstraints.SIZEPOLICY_WANT_GROW), GridConstraints.SIZEPOLICY_FIXED, null, null, null))
  }

  @Contract(pure = true)
  private fun fitTextToLabel(fullText: String?, label: JLabel): String {
    if (fullText == null || fullText.isEmpty()) return " "
    var newFullText = StringUtil.last(fullText, 500, true).toString() // avoid super long strings
    while (label.getFontMetrics(label.font).stringWidth(newFullText) > label.width) {
      val sep = newFullText.indexOf(File.separatorChar, 4)
      if (sep < 0) return newFullText
      newFullText = "..." + newFullText.substring(sep)
    }
    return newFullText
  }

  private fun initDialog(cancelText: @Nls String?) {
    if (SystemInfo.isMac) {
      UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, myText2Label)
    }
    myText2Label.foreground = UIUtil.getContextHelpForeground()

    myCancelButton.addActionListener { doCancelAction() }

    val cancelFunction: (e: ActionEvent) -> Unit = {
      if (myCancelButton.isEnabled) {
        doCancelAction()
      }
    }
    for (shortcut in myProgressWindow.cancelShortcuts) {
      myCancelButton.registerKeyboardAction(cancelFunction, shortcut, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
    }

    if (cancelText != null) {
      myProgressWindow.setCancelButtonText(cancelText)
    }
    myProgressBar.isIndeterminate = myProgressWindow.isIndeterminate
    myProgressBar.maximum = 100
    createCenterPanel()

    myTitlePanel.setActive(true)
    val moveListener = object : WindowMoveListener(myTitlePanel) {
      override fun getView(component: Component): Component {
        return SwingUtilities.getAncestorOfClass(DialogWrapperDialog::class.java, component)
      }
    }
    myTitlePanel.addMouseListener(moveListener)
    myTitlePanel.addMouseMotionListener(moveListener)
  }

  override fun dispose() {
    UIUtil.disposeProgress(myProgressBar)
    UIUtil.dispose(myTitlePanel)
    UIUtil.dispose(myBackgroundButton)
    UIUtil.dispose(myCancelButton)
    myEnableCancelAlarm.cancelAllRequests()
    myDisableCancelAlarm.cancelAllRequests()
  }

  fun getPanel(): JPanel = myPanel

  fun getRepaintRunnable(): Runnable = myRepaintRunnable

  fun getPopup(): DialogWrapper? = myPopup

  fun changeCancelButtonText(text: @Nls String) {
    myCancelButton.text = text
  }

  private fun doCancelAction() {
    if (myProgressWindow.myShouldShowCancel) {
      myProgressWindow.cancel()
    }
  }

  fun cancel() {
    enableCancelButtonIfNeeded(false)
  }

  private fun setCancelButtonEnabledInEDT() {
    myCancelButton.isEnabled = true
  }

  private fun setCancelButtonDisabledInEDT() {
    myCancelButton.isEnabled = false
  }

  fun enableCancelButtonIfNeeded(enable: Boolean) {
    if (myProgressWindow.myShouldShowCancel && !myUpdateAlarm.isDisposed) {
      (if (enable) myEnableCancelAlarm else myDisableCancelAlarm).request()
    }
  }

  private fun createCenterPanel() {
    // Cancel button (if any)

    if (myProgressWindow.myCancelText != null) {
      myCancelButton.text = myProgressWindow.myCancelText
    }
    myCancelButton.isVisible = myProgressWindow.myShouldShowCancel

    myBackgroundButton.isVisible = myShouldShowBackground
    myBackgroundButton.addActionListener {
      if (myShouldShowBackground) {
        myProgressWindow.background()
      }
    }
  }

  @Synchronized
  fun update() {
    if (myRepaintedFlag) {
      if (System.currentTimeMillis() > myLastTimeDrawn + UPDATE_INTERVAL) {
        myRepaintedFlag = false
        UIUtil.invokeLaterIfNeeded(myRepaintRunnable)
      }
      else {
        // later to avoid concurrent dispose/addRequest
        if (!myUpdateAlarm.isDisposed && myUpdateAlarm.isEmpty) {
          UIUtil.invokeLaterIfNeeded {
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
      myBackgroundButton.isEnabled = false
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
        val window = SwingUtilities.getWindowAncestor(myCancelButton)
        if (window != null) {
          val originalFocusOwner = window.mostRecentFocusOwner
          if (originalFocusOwner != null) {
            Disposer.register(myPopup!!.disposable) { originalFocusOwner.requestFocusInWindow() }
          }
        }
        myCancelButton.requestFocusInWindow()
        myRepaintRunnable.run()
      }
    }
  }

  private fun isWriteActionProgress(): Boolean {
    return myProgressWindow is PotemkinProgress
  }

  private fun createDialog(window: Window): MyDialogWrapper {
    if (System.getProperty("vintage.progress") != null || isWriteActionProgress()) {
      if (window.isShowing) {
        return object : MyDialogWrapper(window, myProgressWindow.myShouldShowCancel) {
          override fun useLightPopup(): Boolean {
            return false
          }
        }
      }
      return object : MyDialogWrapper(myProgressWindow.myProject, myProgressWindow.myShouldShowCancel) {
        override fun useLightPopup(): Boolean {
          return false
        }
      }
    }
    if (window.isShowing) {
      return MyDialogWrapper(window, myProgressWindow.myShouldShowCancel)
    }
    return MyDialogWrapper(myProgressWindow.myProject, myProgressWindow.myShouldShowCancel)
  }

  private open inner class MyDialogWrapper : DialogWrapper {
    private val myIsCancellable: Boolean

    constructor(project: Project?, cancellable: Boolean) : super(project, false) {
      init()
      myIsCancellable = cancellable
    }

    constructor(parent: Component, cancellable: Boolean) : super(parent, false) {
      init()
      myIsCancellable = cancellable
    }

    override fun doCancelAction() {
      if (myIsCancellable) {
        this@ProgressDialog.doCancelAction()
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
      myPanel.border = PopupBorder.Factory.create(true, true)
    }

    override fun isProgressDialog(): Boolean {
      return true
    }

    override fun createCenterPanel(): JComponent {
      return myPanel
    }

    override fun createSouthPanel(): JComponent? {
      return null
    }

    override fun createContentPaneBorder(): Border? {
      return null
    }
  }
}