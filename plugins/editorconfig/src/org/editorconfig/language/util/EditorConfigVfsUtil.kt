// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.editorconfig.language.filetype.EditorConfigFileType
import org.editorconfig.language.index.isIndexing

object EditorConfigVfsUtil {
  /**
   * This very fast method does not take into account non-project files.
   * If you are finding all parent .editorcong`s and precision matters more than speed,
   * Consider using EditorConfigFileHierarchyService or EditorConfigPsiTreeUtil#findAllParetnsPsi()
   */
  fun getEditorConfigFiles(project: Project): Collection<VirtualFile> {
    // Not allowed during indexing to prevent reentrant indexing (IDEA-277028)
    if (isIndexing.get() == true) {
      return emptyList()
    }
    ApplicationManager.getApplication().assertReadAccessAllowed()
    val allScope = GlobalSearchScope.allScope(project)
    val filesScope = GlobalSearchScope.getScopeRestrictedByFileTypes(allScope, EditorConfigFileType)
    return FileTypeIndex.getFiles(EditorConfigFileType, filesScope)
  }
}
