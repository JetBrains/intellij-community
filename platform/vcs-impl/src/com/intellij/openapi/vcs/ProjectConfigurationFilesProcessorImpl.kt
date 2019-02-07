// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs

import com.intellij.CommonBundle
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.SelectFilesDialog
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.project.getProjectStoreDirectory
import com.intellij.project.isDirectoryBased
import com.intellij.util.containers.ContainerUtil

private val LOG = Logger.getInstance(ProjectConfigurationFilesProcessorImpl::class.java)

private val configurationFilesExtensionsOutsideStoreDirectory = ContainerUtil.newHashSet(
  ProjectFileType.DEFAULT_EXTENSION,
  ModuleFileType.DEFAULT_EXTENSION)

private const val SHARE_PROJECT_CONFIGURATION_FILES_PROPERTY = "SHARE_PROJECT_CONFIGURATION_FILES"
private const val ASKED_SHARE_PROJECT_CONFIGURATION_FILES_PROPERTY = "ASKED_SHARE_PROJECT_CONFIGURATION_FILES"

class ProjectConfigurationFilesProcessorImpl(private val project: Project,
                                             private val vcsNotifier: VcsNotifier,
                                             private val propertiesComponent: PropertiesComponent) : ProjectConfigurationFilesProcessor {

  private val files = mutableSetOf<VirtualFile>()

  private val NOTIFICATION_LOCK = Object()

  private var notification: Notification? = null

  override fun processFiles(files: List<VirtualFile>, listener: VcsVFSListener): List<VirtualFile> {
    val projectBasePath = project.basePath ?: return files
    val projectBaseDir = LocalFileSystem.getInstance().findFileByPath(projectBasePath) ?: return files
    val storeDir = getProjectStoreDirectory(projectBaseDir)
    if (project.isDirectoryBased && storeDir == null) {
      LOG.warn("Cannot find store directory for project in directory ${projectBaseDir.path}")
      return files
    }

    val projectConfigurationFiles = files.filter {
      configurationFilesExtensionsOutsideStoreDirectory.contains(it.extension) || isProjectConfigurationFile(storeDir, it)
    }

    if (projectConfigurationFiles.isEmpty()) return files

    addNewFiles(projectConfigurationFiles)

    if (needShareFiles()) {
      listener.performAdding(acquireValidFiles(), emptyMap())
    }
    else {
      proposeAddFilesToVcs(listener)
    }

    return files - projectConfigurationFiles
  }

  private fun proposeAddFilesToVcs(listener: VcsVFSListener) {
    synchronized(NOTIFICATION_LOCK) {
      if (notAskedToShareFiles() && notificationNotPresent()) {
        notification = vcsNotifier.notifyMinorInfo("",
                                                   VcsBundle.message("project.configuration.files.add.notification.message",
                                                                     listener.myVcs.displayName),
                                                   showAction(listener), addAction(listener), muteAction())
      }
    }
  }

  private fun showAction(listener: VcsVFSListener) = NotificationAction.createSimple(
    VcsBundle.getString("project.configuration.files.add.notification.action.view")) {
    val allProjectFiles = acquireValidFiles()
    if (allProjectFiles.isNotEmpty()) {
      with(SelectFilesDialog.init(project, allProjectFiles, null, null, true, true,
                                   CommonBundle.getAddButtonText(), CommonBundle.getCancelButtonText())) {
        selectedFiles = allProjectFiles
        if (showAndGet()) {
          val userSelectedFiles = selectedFiles
          listener.performAdding(userSelectedFiles, emptyMap())
          removeFiles(userSelectedFiles)
          if(isFilesEmpty()){
            expireNotification()
          }
        }
      }
    }
  }

  @Synchronized
  private fun removeFiles(projectConfigurationFiles: Collection<VirtualFile>) {
    files.removeAll(projectConfigurationFiles)
  }

  @Synchronized
  private fun isFilesEmpty() = files.isEmpty()

  @Synchronized
  private fun addNewFiles(projectConfigurationFiles: Collection<VirtualFile>) {
    files.addAll(projectConfigurationFiles)
  }

  @Synchronized
  private fun acquireValidFiles(): List<VirtualFile> {
    files.removeAll { !it.isValid }
    return files.toList()
  }

  @Synchronized
  private fun clearFiles() {
    files.clear()
  }

  private fun addAction(listener: VcsVFSListener) = NotificationAction.create(
    VcsBundle.getString("project.configuration.files.add.notification.action.add")) { _, _ ->
    listener.performAdding(acquireValidFiles(), emptyMap())
    propertiesComponent.setValue(SHARE_PROJECT_CONFIGURATION_FILES_PROPERTY, true)
    propertiesComponent.setValue(ASKED_SHARE_PROJECT_CONFIGURATION_FILES_PROPERTY, true)
    expireNotification()
    clearFiles()
  }

  private fun muteAction() = NotificationAction.create(
    VcsBundle.getString("project.configuration.files.add.notification.action.mute")) { _, notification ->
    propertiesComponent.setValue(SHARE_PROJECT_CONFIGURATION_FILES_PROPERTY, false)
    propertiesComponent.setValue(ASKED_SHARE_PROJECT_CONFIGURATION_FILES_PROPERTY, true)
    notification.expire()
  }

  private fun notificationNotPresent() =
    synchronized(NOTIFICATION_LOCK) {
      notification?.isExpired ?: true
    }

  private fun expireNotification() =
    synchronized(NOTIFICATION_LOCK) {
      notification?.expire()
    }

  private fun isProjectConfigurationFile(storeDir: VirtualFile?, file: VirtualFile) =
    storeDir != null && VfsUtilCore.isAncestor(storeDir, file, true)

  private fun notAskedToShareFiles() = !propertiesComponent.getBoolean(ASKED_SHARE_PROJECT_CONFIGURATION_FILES_PROPERTY, false)

  private fun needShareFiles() = propertiesComponent.getBoolean(SHARE_PROJECT_CONFIGURATION_FILES_PROPERTY, false)
}