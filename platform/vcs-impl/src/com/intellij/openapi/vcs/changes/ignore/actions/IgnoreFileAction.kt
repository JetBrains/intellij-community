// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ignore.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.IgnoredBeanFactory
import com.intellij.openapi.vcs.changes.actions.ScheduleForAdditionAction
import com.intellij.openapi.vcs.changes.ignore.psi.util.addNewElements
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.isEmpty

class IgnoreFileAction(private val ignoreFile: VirtualFile) : DumbAwareAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData<Project>(CommonDataKeys.PROJECT)
    val ignored =
      e.getRequiredData<Array<VirtualFile>>(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        .map { IgnoredBeanFactory.ignoreFile(it, project) }
        .toTypedArray()

    addNewElements(project, ignoreFile, *ignored)
    ChangeListManagerImpl.getInstanceImpl(project).scheduleUnversionedUpdate()
    OpenFileDescriptor(project, ignoreFile).navigate(true)
  }

  override fun update(e: AnActionEvent) {
    val project = e.project ?: return
    e.presentation.isVisible = !ScheduleForAdditionAction.getUnversionedFiles(e, project).isEmpty()
  }
}