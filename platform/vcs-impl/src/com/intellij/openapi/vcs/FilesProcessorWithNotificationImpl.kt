// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs

import com.intellij.CommonBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ui.SelectFilesDialog
import com.intellij.openapi.vfs.VirtualFile

abstract class FilesProcessorWithNotificationImpl(protected val project: Project, parentDisposable: Disposable) : FilesProcessor {
  private val files = mutableSetOf<VirtualFile>()

  private val NOTIFICATION_LOCK = Object()

  private var notification: Notification? = null

  abstract val askedBeforeProperty: String

  abstract val doForCurrentProjectProperty: String?

  abstract val showActionText: String
  abstract val forCurrentProjectActionText: String
  abstract val forAllProjectsActionText: String?
  abstract val muteActionText: String

  abstract fun notificationTitle(): String
  abstract fun notificationMessage(): String

  abstract fun doActionOnChosenFiles(files: Collection<VirtualFile>)

  abstract fun doFilterFiles(files: Collection<VirtualFile>): Collection<VirtualFile>

  abstract fun rememberForAllProjects()

  protected open val viewFilesDialogTitle: String? = null
  protected open val viewFilesDialogOkActionName: String = CommonBundle.getAddButtonText()
  protected open val viewFilesDialogCancelActionName: String = CommonBundle.getCancelButtonText()

  protected open fun rememberForCurrentProject() {
    setForCurrentProject(true)
  }

  init {
    Disposer.register(parentDisposable, this)
  }

  override fun processFiles(files: List<VirtualFile>): List<VirtualFile> {

    val filteredFiles = doFilterFiles(files)

    if (filteredFiles.isEmpty()) return files

    addNewFiles(filteredFiles)

    if (needDoForCurrentProject()) {
      doActionOnChosenFiles(acquireValidFiles())
      clearFiles()
    }
    else {
      proposeToProcessFiles()
    }

    return files - filteredFiles
  }

  private fun proposeToProcessFiles() {
    synchronized(NOTIFICATION_LOCK) {
      if (notAskedBefore() && notificationNotPresent()) {
        val notificationActions = mutableListOf(showAction(), addForCurrentProjectAction()).apply {
          if (forAllProjectsActionText != null) {
            add(forAllProjectsAction())
          }
          add(muteAction())
        }
        notification = VcsNotifier.getInstance(project).notifyMinorInfo(true, notificationTitle(), notificationMessage(), *notificationActions.toTypedArray())
      }
    }
  }

  @Synchronized
  protected fun removeFiles(filesToRemove: Collection<VirtualFile>) {
    files.removeAll(filesToRemove)
  }

  @Synchronized
  private fun isFilesEmpty() = files.isEmpty()

  @Synchronized
  private fun addNewFiles(filesToAdd: Collection<VirtualFile>) {
    files.addAll(filesToAdd)
  }

  @Synchronized
  protected fun acquireValidFiles(): List<VirtualFile> {
    files.removeAll { !it.isValid }
    return files.toList()
  }

  @Synchronized
  private fun clearFiles() {
    files.clear()
  }

  override fun dispose() {
    clearFiles()
  }

  private fun showAction() = NotificationAction.createSimple(showActionText) {
    val allFiles = acquireValidFiles()
    if (allFiles.isNotEmpty()) {
      with(SelectFilesDialog.init(project, allFiles, null, null, true, true,
                                  viewFilesDialogOkActionName, viewFilesDialogCancelActionName)) {
        title = viewFilesDialogTitle
        selectedFiles = allFiles
        if (showAndGet()) {
          val userSelectedFiles = selectedFiles
          doActionOnChosenFiles(userSelectedFiles)
          removeFiles(userSelectedFiles)
          if (isFilesEmpty()) {
            expireNotification()
          }
        }
      }
    }
  }

  private fun addForCurrentProjectAction() = NotificationAction.create(forCurrentProjectActionText) { _, _ ->
    doActionOnChosenFiles(acquireValidFiles())
    rememberForCurrentProject()
    PropertiesComponent.getInstance(project).setValue(askedBeforeProperty, true)
    expireNotification()
    clearFiles()
  }

  private fun forAllProjectsAction() = NotificationAction.create(forAllProjectsActionText!!) { _, _ ->
    doActionOnChosenFiles(acquireValidFiles())
    rememberForCurrentProject()
    PropertiesComponent.getInstance(project).setValue(askedBeforeProperty, true)
    rememberForAllProjects()
    expireNotification()
    clearFiles()
  }

  private fun muteAction() = NotificationAction.create(muteActionText) { _, notification ->
    setForCurrentProject(false)
    PropertiesComponent.getInstance(project).setValue(askedBeforeProperty, true)
    notification.expire()
  }

  protected fun notificationNotPresent() =
    synchronized(NOTIFICATION_LOCK) {
      notification?.isExpired ?: true
    }

  protected fun expireNotification() =
    synchronized(NOTIFICATION_LOCK) {
      notification?.expire()
    }

  private fun setForCurrentProject(value: Boolean) {
    doForCurrentProjectProperty?.let {
      PropertiesComponent.getInstance(project).setValue(it, value)
    }
  }

  private fun getForCurrentProject(): Boolean {
    return doForCurrentProjectProperty?.let { PropertiesComponent.getInstance(project).getBoolean(it, false) } ?: false
  }

  private fun notAskedBefore() = !wasAskedBefore()

  protected fun wasAskedBefore() = PropertiesComponent.getInstance(project).getBoolean(askedBeforeProperty, false)

  protected open fun needDoForCurrentProject() = getForCurrentProject()
}