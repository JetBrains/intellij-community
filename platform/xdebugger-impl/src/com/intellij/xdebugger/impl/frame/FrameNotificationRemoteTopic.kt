// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.debugger.impl.rpc.FrameNotificationRequest
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy
import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import com.intellij.platform.rpc.topics.sendToClient
import com.intellij.ui.awt.AnchoredPoint
import com.intellij.util.asDisposable
import com.intellij.util.ui.EDT
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.SplitDebuggerMode
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import com.intellij.xdebugger.impl.proxy.asProxy
import com.intellij.xdebugger.impl.ui.XDebugSessionTab
import org.jetbrains.annotations.ApiStatus
import javax.swing.plaf.basic.BasicArrowButton

@ApiStatus.Internal
object FrameNotificationUtils {
  val FRAME_NOTIFICATION_REMOTE_TOPIC: ProjectRemoteTopic<FrameNotificationRequest> = ProjectRemoteTopic("xdebugger.show.frame.notification", FrameNotificationRequest.serializer())

  @JvmStatic
  fun showNotification(project: Project, session: XDebugSession?, @NlsSafe content: String) {
    if (SplitDebuggerMode.isSplitDebugger()) {
      val sessionId = (session as? XDebugSessionImpl)?.id
      FRAME_NOTIFICATION_REMOTE_TOPIC.sendToClient(project, FrameNotificationRequest(sessionId, content))
    }
    else {
      showNotificationImpl(project, session?.asProxy(), content)
    }
  }

  fun showNotificationImpl(project: Project, session: XDebugSessionProxy?, @NlsSafe content: String) {
    EDT.assertIsEdt()
    val messageType = MessageType.INFO
    if (session != null) {
      val tab = session.sessionTab as? XDebugSessionTab
      if (tab != null) {
        val view = tab.framesView
        if (view != null) {
          val comboBox = view.threadComboBox
          val arrowButton = UIUtil.findComponentOfType(comboBox, BasicArrowButton::class.java)

          val target = arrowButton ?: comboBox

          val balloonBuilder = JBPopupFactory.getInstance()
            .createHtmlTextBalloonBuilder(content, messageType, null)
            .setHideOnClickOutside(true)
            .setDisposable(createEdtDisposable(session.coroutineScope.asDisposable()))
            .setHideOnFrameResize(false)
          val balloon = balloonBuilder.createBalloon()
          balloon.show(AnchoredPoint(AnchoredPoint.Anchor.TOP, target), Balloon.Position.above)
          return
        }
      }
    }

    // Fallback to the whole toolwindow notification
    XDebuggerManagerImpl.getNotificationGroup()
      .createNotification(content, messageType)
      .notify(project)
  }
}

private fun createEdtDisposable(parentDisposable: Disposable): Disposable {
  EDT.assertIsEdt()
  val result = Disposer.newCheckedDisposable()
  val isSuccess = Disposer.tryRegister(parentDisposable) {
    runInEdt {
      Disposer.dispose(result)
    }
  }
  if (!isSuccess) {
    runInEdt {
      Disposer.dispose(result)
    }
  }
  return result
}