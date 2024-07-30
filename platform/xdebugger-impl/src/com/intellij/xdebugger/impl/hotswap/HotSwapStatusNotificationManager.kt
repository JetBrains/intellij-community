// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.hotswap

import com.intellij.execution.multilaunch.design.components.RoundedCornerBorder
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.BalloonImpl
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.XDebuggerBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Point
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.time.Duration.Companion.seconds

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class HotSwapStatusNotificationManager(private val project: Project) : Disposable.Default {
  companion object {
    fun getInstance(project: Project): HotSwapStatusNotificationManager = project.service()
  }

  fun showSuccessNotification(scope: CoroutineScope, disposable: Disposable? = null) {
    scope.launch(Dispatchers.EDT) {
      val frame = WindowManager.getInstance().getFrame(project) ?: return@launch
      val factory = JBPopupFactory.getInstance() ?: return@launch

      val balloon = factory.createBalloonBuilder(SuccessfulHotSwapComponent())
        .setBorderColor(JBColor.border())
        .setCornerRadius(JBUI.scale(RADIUS))
        .setBorderInsets(JBUI.emptyInsets())
        .setFadeoutTime(NOTIFICATION_TIME_SECONDS.seconds.inWholeMilliseconds)
        .setBlockClicksThroughBalloon(true)
        .setHideOnAction(false)
        .setHideOnClickOutside(false)
        .apply { if (disposable != null) setDisposable(disposable) }
        .createBalloon()

      if (balloon is BalloonImpl) {
        balloon.setShowPointer(false)
      }
      balloon.show(RelativePoint(frame, Point(frame.width / 2, 2 * frame.height / 3)), Balloon.Position.below)
    }
  }
}

private const val RADIUS = 10
private const val NOTIFICATION_TIME_SECONDS = 3

private class SuccessfulHotSwapComponent : JPanel(BorderLayout()) {
  init {
    isOpaque = false
    border = RoundedCornerBorder(JBUI.scale(RADIUS))
    add(JBLabel(XDebuggerBundle.message("xdebugger.hotswap.status.success"), AllIcons.Status.Success, SwingConstants.CENTER))
  }
}
