// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs

import com.intellij.CommonBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.changes.ui.SelectFilesDialog

abstract class FilesProcessorWithNotificationImpl(
  project: Project,
  parentDisposable: Disposable
) : FilesProcessorImpl(project, parentDisposable) {

  private val NOTIFICATION_LOCK = Object()

  private var notification: Notification? = null
  abstract val notificationDisplayId: String

  abstract val showActionText: String
  abstract val forCurrentProjectActionText: String
  abstract val forAllProjectsActionText: String?
  abstract val muteActionText: String

  @NlsContexts.NotificationTitle
  abstract fun notificationTitle(): String
  @NlsContexts.NotificationContent
  abstract fun notificationMessage(): String
  @NlsContexts.DialogTitle
  protected open val viewFilesDialogTitle: String? = null
  @NlsContexts.Button
  protected open val viewFilesDialogOkActionName: String = CommonBundle.getAddButtonText()
  @NlsContexts.Button
  protected open val  viewFilesDialogCancelActionName: String = CommonBundle.getCancelButtonText()

  override fun doProcess(): Boolean {
    val processed = super.doProcess()
    if (!processed) {
      proposeToProcessFiles()
    }
    return processed
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
        notification = VcsNotifier.getInstance(project).notifyMinorInfo(
          notificationDisplayId,
          true,
          notificationTitle(),
          notificationMessage(),
          *notificationActions.toTypedArray()
        )
      }
    }
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
  }
