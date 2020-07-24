// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContextRepository

internal class GHPREditorProvider : FileEditorProvider, DumbAware {
  override fun accept(project: Project, file: VirtualFile): Boolean {
    return file is GHPRVirtualFile && GHPRDataContextRepository.getInstance(project).findContext(file.repository) != null
  }

  override fun createEditor(project: Project, file: VirtualFile): GHPRFileEditor {
    file as GHPRVirtualFile
    val dataContext = GHPRDataContextRepository.getInstance(project).findContext(file.repository)!!
    return GHPRFileEditor(project, dataContext, file.pullRequest)
  }

  override fun getEditorTypeId(): String = "GHPR"
  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}