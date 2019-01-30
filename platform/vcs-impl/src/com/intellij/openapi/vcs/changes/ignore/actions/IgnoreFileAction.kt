// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ignore.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.IgnoredBeanFactory
import com.intellij.openapi.vcs.changes.ignore.psi.util.addNewElementsToIgnoreBlock
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.FileContentUtil

class IgnoreFileAction(private val ignoreFile: VirtualFile) : AnAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val files = e.getRequiredData<Array<VirtualFile>>(CommonDataKeys.VIRTUAL_FILE_ARRAY)
    val project = e.getRequiredData<Project>(CommonDataKeys.PROJECT)
    val ignores = files.map { IgnoredBeanFactory.ignoreFile(it, project) }.toTypedArray()

    addNewElementsToIgnoreBlock(project, ignoreFile, "", *ignores)
    FileContentUtil.reparseFiles(project, listOf(ignoreFile), true)
    OpenFileDescriptor(project, ignoreFile).navigate(true)
  }
}