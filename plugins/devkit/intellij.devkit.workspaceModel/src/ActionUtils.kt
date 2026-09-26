// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry

internal fun isIntellijProjectOrRegistryKeyIsSet(project: Project?): Boolean =
  project?.let {
    IntelliJProjectUtil.isIntelliJPlatformProject(project) || Registry.`is`("devkit.workspaceModel.code.generation", false)
  } ?: false

internal fun generationNotAvailableNotification(project: Project) {
  val groupId = DevKitWorkspaceModelBundle.message("notification.workspace.code.generation.not.available")
  val content = DevKitWorkspaceModelBundle.message("notification.workspace.code.generation.not.available.message")
  NotificationGroupManager.getInstance()
    .getNotificationGroup(groupId)
    .createNotification(title = groupId, content = content, type = NotificationType.INFORMATION)
    .notify(project)
}