// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.editorconfig.language.filetype.EditorConfigFileType

object EditorConfigVfsUtil {
  fun getEditorConfigFiles(project: Project): Collection<VirtualFile> {
    return FileTypeIndex.getFiles(EditorConfigFileType, GlobalSearchScope.allScope(project))
  }
}
