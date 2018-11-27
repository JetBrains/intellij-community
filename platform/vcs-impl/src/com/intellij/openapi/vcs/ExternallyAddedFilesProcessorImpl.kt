// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

private const val ADD_EXTERNAL_FILES_PROPERTY = "ADD_EXTERNAL_FILES"
private const val ASKED_ADD_EXTERNAL_FILES_PROPERTY = "ASKED_ADD_EXTERNAL_FILES"

class ExternallyAddedFilesProcessorImpl(project: Project,
                                        private val vcsName: String,
                                        private val addChosenFiles: (Collection<VirtualFile>) -> Unit)
  : FilesProcessorWithNotificationImpl(project), FilesProcessor {

  override val askedBeforeProperty = ASKED_ADD_EXTERNAL_FILES_PROPERTY
  override val doForCurrentProjectProperty = ADD_EXTERNAL_FILES_PROPERTY

  override val showActionText: String = VcsBundle.message("external.files.add.notification.action.view")
  override val forCurrentProjectActionText: String = VcsBundle.message("external.files.add.notification.action.add")
  override val forAllProjectsActionText: String? = null
  override val muteActionText: String = VcsBundle.message("external.files.add.notification.action.mute")

  override fun notificationTitle() = ""

  override fun notificationMessage(): String = VcsBundle.message("external.files.add.notification.message", vcsName)

  override fun doActionOnChosenFiles(files: Collection<VirtualFile>) {
    addChosenFiles(files)
  }

  override fun doFilterFiles(files: Collection<VirtualFile>) = files

  override fun rememberForAllProjects() {}
}