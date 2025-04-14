// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl

import com.intellij.concurrency.currentThreadContext
import com.intellij.openapi.client.currentSession
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.FileVisibilityProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.WritingAccessProvider
import com.intellij.serviceContainer.contextComponentManager

internal class FilePermissionManagerImpl: FilePermissionManager {
  override fun isReadGranted(file: VirtualFile): Boolean {
    val project = getProject() ?: return false

    return checkFileVisible(project, file)
  }

  override fun isWriteGranted(file: VirtualFile): Boolean {
    val project = getProject() ?: return false

    return checkFileVisible(project, file) && checkFileWritable(project, file)
  }

  private fun getProject(): Project? {
    val componentManager = currentThreadContext().contextComponentManager()
    val project = componentManager.getService(Project::class.java)

    if (project == null) {
      logger.warn("Can't get a project out of the current thread context. Current component manager: ${componentManager}")
      return null
    }

    return project
  }

  private fun checkFileWritable(project: Project, file: VirtualFile): Boolean {
    return WritingAccessProvider.isPotentiallyWritable(file, project)
  }

  private fun checkFileVisible(project: Project, file: VirtualFile): Boolean {
    return FileVisibilityProvider.isVisible(project, file, project.currentSession.isOwner)
  }

  companion object {
    val logger = logger<FilePermissionManagerImpl>()
  }
}