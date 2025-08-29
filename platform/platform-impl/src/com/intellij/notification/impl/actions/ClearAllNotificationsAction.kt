// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl.actions

import com.intellij.notification.ActionCenter
import com.intellij.notification.impl.ApplicationNotificationsModel
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction

internal class ClearAllNotificationsAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabled = ApplicationNotificationsModel.getNotifications(project).isNotEmpty() ||
                               (project != null && !ApplicationNotificationsModel.isEmptyContent(project))
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    if (project != null) {
      ActionCenter.expireNotifications(project)
    }
    ApplicationNotificationsModel.clearAll(e.project)
  }

  companion object {
    //language=devkit-action-id
    const val ID: String = "ClearAllNotifications"
  }
}