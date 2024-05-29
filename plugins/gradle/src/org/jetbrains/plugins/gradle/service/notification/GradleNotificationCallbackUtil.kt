// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleNotificationCallbackUtil")

package org.jetbrains.plugins.gradle.service.notification

import com.intellij.openapi.externalSystem.service.notification.NotificationData
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem

fun navigateByNotificationData(project: Project, notificationData: NotificationData) {
  val filePath = notificationData.filePath ?: return
  val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath) ?: return
  val line = notificationData.line - 1
  val column = notificationData.column - 1
  val guiLine = if (line < 0) -1 else line
  val guiColumn = if (column < 0) -1 else column + 1
  OpenFileDescriptor(project, virtualFile, guiLine, guiColumn).navigate(true)
}
