// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

internal class GHPREditorProvider : FileEditorProvider, DumbAware {

  override fun accept(project: Project, file: VirtualFile): Boolean =
    file is GHPRTimelineVirtualFile && file.isValid

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    file as GHPRTimelineVirtualFile
    return project.service<GHPREditorProviderService>().createTimelineEditor(file)
  }

  override fun getEditorTypeId(): String = "GHPR"
  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

@Service(Service.Level.PROJECT)
private class GHPREditorProviderService(parentCs: CoroutineScope) {
  private val cs = parentCs.childScope(Dispatchers.Main)

  fun createTimelineEditor(file: GHPRTimelineVirtualFile): FileEditor {
    val projectVm = file.findProjectVm() ?: throw ProcessCanceledException()
    return GHPRTimelineFileEditor(cs, projectVm, file)
  }
}
