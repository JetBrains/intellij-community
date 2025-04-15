// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.services

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.editorconfig.language.psi.EditorConfigPsiFile

sealed class EditorConfigServiceResult
data class EditorConfigServiceLoaded(val list: List<EditorConfigPsiFile>) : EditorConfigServiceResult()
object EditorConfigServiceLoading : EditorConfigServiceResult()

abstract class EditorConfigFileHierarchyService {
  abstract fun getParentEditorConfigFiles(virtualFile: VirtualFile): EditorConfigServiceResult

  companion object {
    @JvmStatic
    fun getInstance(project: Project): EditorConfigFileHierarchyService {
      return project.getService(EditorConfigFileHierarchyService::class.java)
    }
  }
}
