// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.vfs

import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import git4idea.i18n.GitBundle

class GitIndexVirtualFileTabTitleProvider : EditorTabTitleProvider {
  private fun isApplicable(file: VirtualFile): Boolean = file is GitIndexVirtualFile

  override fun getEditorTabTitle(project: Project, file: VirtualFile): String? = null

  override fun getEditorTabTooltipText(project: Project, file: VirtualFile): String? {
    if (!isApplicable(file)) return null
    return GitBundle.message("stage.vfs.editor.tab.tooltip",
                             FileUtil.getLocationRelativeToUserHome(file.filePath().presentableUrl))
  }
}