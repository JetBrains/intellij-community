// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapper.IdeModalityType
import com.intellij.openapi.ui.DialogWrapper.PeerFactory
import com.intellij.openapi.ui.DialogWrapperPeer
import com.intellij.openapi.ui.DialogWrapperPeerFactory
import com.intellij.openapi.ui.impl.GlassPaneDialogWrapperPeer
import com.intellij.openapi.ui.impl.GlassPaneDialogWrapperPeer.GlasspanePeerUnavailableException
import com.intellij.ui.PopupBorder
import java.awt.Component
import java.awt.Window
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JRootPane
import javax.swing.border.Border

internal class ProgressDialogWrapper(
  private val panel: JPanel,
  private val cancelAction: () -> Unit,
  peerFactory: PeerFactory,
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

fun createDialogWrapper(
  panel: JPanel,
  cancelAction: () -> Unit,
  window: Window,
  lightPopup: Boolean,
  project: Project?,
): DialogWrapper {
  if (window.isShowing) {
    return ProgressDialogWrapper(panel, cancelAction, peerFactory(window, lightPopup))
  }
  else {
    return ProgressDialogWrapper(panel, cancelAction, peerFactory(project)).also {
      it.superInitResizeListener()
    }
  }
}

private fun peerFactory(window: Window, lightPopup: Boolean): PeerFactory = PeerFactory { dialogWrapper ->
  if (lightPopup && System.getProperty("vintage.progress") == null) {
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

private fun peerFactory(project: Project?): PeerFactory = PeerFactory { dialogWrapper ->
  DialogWrapperPeerFactory.getInstance().createPeer(dialogWrapper, project, false, IdeModalityType.IDE)
}
