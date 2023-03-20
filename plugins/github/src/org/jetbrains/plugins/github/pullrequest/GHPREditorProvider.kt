// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContextRepository

internal class GHPREditorProvider : FileEditorProvider, DumbAware {

  override fun accept(project: Project, file: VirtualFile): Boolean =
    file is GHPRTimelineVirtualFile && GHPRDataContextRepository.getInstance(project).findContext(file.repository) != null

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    file as GHPRTimelineVirtualFile
    val dataContext = GHPRDataContextRepository.getInstance(project).findContext(file.repository)!!

    val dataDisposable = Disposer.newDisposable()
    val dataProvider = dataContext.dataProviderRepository.getDataProvider(file.pullRequest, dataDisposable)

    return GHPRTimelineFileEditor(project, dataContext, dataProvider, file).also {
      Disposer.register(it, dataDisposable)
    }
  }

  override fun getEditorTypeId(): String = "GHPR"
  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
