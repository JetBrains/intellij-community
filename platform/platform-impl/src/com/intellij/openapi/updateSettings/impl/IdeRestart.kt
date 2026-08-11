// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.IdeBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JProgressBar
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.jetbrains.annotations.ApiStatus

fun restartOrNotify(
  project: Project,
  restartAutomatically: Boolean,
  @NlsContexts.DialogTitle progressTitle: String = IdeBundle.message("action.UpdateIde.progress.title"),
  restart: () -> Unit,
) {
  if (restartAutomatically) {
    ApplicationManager.getApplication().invokeLater {
      scheduleRestart(project, progressTitle) { restart() }
    }
  }
  else {
    showRestartNotification(project, restart)
  }
}

private fun showRestartNotification(project: Project, restart: () -> Unit) {
  @Suppress("DialogTitleCapitalization")
  Notification("IDE and Plugin Updates", IdeBundle.message("action.UpdateIde.task.success.title"),
               IdeBundle.message("action.UpdateIde.task.success.content"), NotificationType.INFORMATION)
    .addAction(NotificationAction.createSimpleExpiring(
      IdeBundle.message("action.UpdateIde.task.success.restart")) { restart() })
    .notify(project)
}

private fun scheduleRestart(
  project: Project,
  @NlsContexts.DialogTitle progressTitle: String,
  restart: () -> Unit,
) {
  CountdownDialog(project, progressTitle, timeout = 10.seconds, action = restart).show()
}

internal class CountdownDialog(
  project: Project?,
  @NlsContexts.DialogTitle dialogTitle: String,
  private val timeout: Duration,
  private val action: () -> Unit,
) : DialogWrapper(project) {

  private val countdownLabel = JBLabel()
  private val progressBar = JProgressBar(0, 100)

  init {
    title = dialogTitle
    setUndecorated(true)
    setShouldUseWriteIntentReadAction(false)
    init()
    startCountdown()
  }

  override fun createCenterPanel(): JComponent {
    countdownLabel.text = IdeBundle.message("action.UpdateIde.progress.text.ide.will.restart", timeout)

    progressBar.value = 100
    progressBar.preferredSize = JBUI.size(300, progressBar.preferredSize.height)

    val progressPanel = JPanel(BorderLayout()).apply {
      border = JBUI.Borders.emptyTop(10)
      add(progressBar, BorderLayout.CENTER)
    }

    return JPanel(BorderLayout()).apply {
      border = JBUI.Borders.empty(10)
      add(countdownLabel, BorderLayout.NORTH)
      add(progressPanel, BorderLayout.CENTER)
    }
  }

  override fun createDefaultActions() {
    super.createDefaultActions()
    myCancelAction.putValue(Action.NAME, IdeBundle.message("action.UpdateIde.button.postpone"))
  }

  override fun createActions(): Array<Action> {
    val restartNowAction = RestartNowAction()
    return arrayOf(restartNowAction, cancelAction)
  }

  private inner class RestartNowAction : DialogWrapperAction(IdeBundle.message("action.UpdateIde.task.success.restart")) {
    override fun doAction(e: ActionEvent) {
      finishSuccessfully()
    }
  }

  private fun finishSuccessfully() {
    close(OK_EXIT_CODE)
    service<ActionService>().scope.launch(Dispatchers.EDT) {
      action()
    }
  }

  private fun startCountdown() {
    rootPane.launchOnShow("count-down") {
      val updateInterval = 25.milliseconds

      val startedMs = System.currentTimeMillis()
      while (true) {
        val elapsed = (System.currentTimeMillis() - startedMs).milliseconds
        if (elapsed >= timeout) break

        val remainingSeconds = (timeout - elapsed).inWholeSeconds + 1
        countdownLabel.text = IdeBundle.message("action.UpdateIde.progress.text.ide.will.restart", remainingSeconds)
        progressBar.value = ((timeout - elapsed) * 100 / timeout).toInt()
        delay(updateInterval)
      }

      ensureActive()

      finishSuccessfully()
    }
  }

  @Service(Service.Level.APP)
  private class ActionService(val scope: CoroutineScope)
}
