// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.frame

import com.intellij.CommonBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.WindowManager
import com.intellij.platform.debugger.impl.shared.ShowImagePopupUtil
import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import com.intellij.platform.rpc.topics.ProjectRemoteTopicListener
import com.intellij.ui.awt.RelativePoint
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Point
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingConstants

@ApiStatus.Internal
interface ImageEditorUIProvider {
  fun createImageEditorUI(imageData: ByteArray): JComponent
}

@ApiStatus.Internal // might be public in theory
object ImageEditorUIUtil {
  private val EP_NAME = ExtensionPointName<ImageEditorUIProvider>("com.intellij.xdebugger.imageEditorUIProvider")

  fun canCreateImageEditor(): Boolean = EP_NAME.hasAnyExtensions()

  fun createImageEditorUI(imageData: ByteArray): JComponent? = EP_NAME.extensionList.firstOrNull()?.createImageEditorUI(imageData)
}

internal class ShowImagePopupRemoteTopicListener : ProjectRemoteTopicListener<ShowImagePopupUtil.Request> {
  override val topic: ProjectRemoteTopic<ShowImagePopupUtil.Request> = ShowImagePopupUtil.REMOTE_TOPIC

  override fun handleEvent(project: Project, event: ShowImagePopupUtil.Request) {
    runInEdt {
      val frame = WindowManager.getInstance().getFrame(project)
      if (frame == null) return@runInEdt
      val popupSize = Dimension(frame.size.width / 2, frame.size.height / 2)

      val imageData = event.imageData
      val content =
        if (imageData != null) ImageEditorUIUtil.createImageEditorUI(imageData) ?: return@runInEdt
        else JLabel(CommonBundle.message("label.no.data"), SwingConstants.CENTER)

      val popup = DebuggerUIUtil.createValuePopup(project, content, null)
      if (content is Disposable) {
        Disposer.register(popup, content)
      }

      popup.setSize(popupSize)
      popup.show(RelativePoint(frame, Point(popupSize.width / 2, popupSize.height / 2)))
    }
  }
}