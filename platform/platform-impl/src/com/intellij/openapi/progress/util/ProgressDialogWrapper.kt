// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapper.IdeModalityType
import com.intellij.openapi.ui.DialogWrapperPeer
import com.intellij.openapi.ui.DialogWrapperPeerFactory
import com.intellij.openapi.ui.impl.DialogWrapperPeerImpl
import com.intellij.openapi.ui.impl.GlassPaneDialogWrapperPeer
import com.intellij.openapi.ui.impl.GlassPaneDialogWrapperPeer.GlasspanePeerUnavailableException
import com.intellij.ui.PopupBorder
import java.awt.Component
import java.awt.Window
import java.util.function.Function
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JRootPane
import javax.swing.border.Border

internal class ProgressDialogWrapper(
  private val panel: JPanel,
  private val cancelAction: () -> Unit,
  peerFactory: Function<DialogWrapper, DialogWrapperPeer>,
) : DialogWrapper(peerFactory) {

  init {
    init()
  }

  override fun doCancelAction() {
    cancelAction()
  }

  override fun init() {
    super.init()
    setUndecorated(true)
    rootPane.windowDecorationStyle = JRootPane.NONE
    panel.border = PopupBorder.Factory.create(true, true)
  }

  fun superInitResizeListener() {
    super.initResizeListener()
  }

  override fun isProgressDialog(): Boolean {
    return true
  }

  override fun createCenterPanel(): JComponent {
    return panel
  }

  override fun createContentPaneBorder(): Border? {
    return null
  }

  override fun createPeer(parent: Component, canBeParent: Boolean): DialogWrapperPeer {
    error("must not be called")
  }

  override fun createPeer(owner: Window?, canBeParent: Boolean, applicationModalIfPossible: Boolean): DialogWrapperPeer {
    error("must not be called")
  }

  override fun createPeer(project: Project?, canBeParent: Boolean): DialogWrapperPeer {
    error("must not be called")
  }

  override fun createPeer(owner: Window?, canBeParent: Boolean, ideModalityType: IdeModalityType?): DialogWrapperPeer {
    error("must not be called")
  }

  override fun createPeer(project: Project?, canBeParent: Boolean, ideModalityType: IdeModalityType): DialogWrapperPeer {
    error("must not be called")
  }
}

internal fun createDialogWrapper(
  panel: JPanel,
  cancelAction: () -> Unit,
  window: Window,
  writeAction: Boolean,
  project: Project?,
): DialogWrapper {
  val dialog = if (window.isShowing) {
    ProgressDialogWrapper(panel, cancelAction, peerFactory(window, !writeAction))
  }
  else {
    ProgressDialogWrapper(panel, cancelAction, peerFactory(project)).also {
      it.superInitResizeListener()
    }
  }
  setupProgressDialog(dialog, writeAction)
  return dialog
}

private fun peerFactory(window: Window, lightPopup: Boolean): Function<DialogWrapper, DialogWrapperPeer> {
  return java.util.function.Function { dialogWrapper ->
    if (lightPopup) {
      try {
        GlassPaneDialogWrapperPeer(dialogWrapper, window)
      }
      catch (e: GlasspanePeerUnavailableException) {
        DialogWrapperPeerFactory.getInstance().createPeer(dialogWrapper, window, false)
      }
    }
    else {
      DialogWrapperPeerFactory.getInstance().createPeer(dialogWrapper, window, false)
    }
  }
}

private fun peerFactory(project: Project?): Function<DialogWrapper, DialogWrapperPeer> {
  return Function { dialogWrapper ->
    DialogWrapperPeerFactory.getInstance().createPeer(dialogWrapper, project, false, IdeModalityType.IDE)
  }
}

internal fun setupProgressDialog(dialog: DialogWrapper, writeAction: Boolean) {
  dialog.setUndecorated(true)
  val peer = dialog.peer
  if (peer is DialogWrapperPeerImpl) {
    peer.setAutoRequestFocus(false)
    if (writeAction) {
      dialog.isModal = false // display the dialog and continue with EDT execution, don't block it forever
    }
  }
  dialog.pack()
}
