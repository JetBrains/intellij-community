// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.vfs

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vcs.vfs.CustomisableUniqueNameEditorTabTitleProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import git4idea.i18n.GitBundle

internal class GitIndexVirtualFileTabTitleProvider : CustomisableUniqueNameEditorTabTitleProvider() {
  override fun isApplicable(file: VirtualFile): Boolean = file is GitIndexVirtualFile

  override fun getEditorTabTitle(file: VirtualFile, baseUniqueName: String): String {
    return GitBundle.message("stage.vfs.presentable.file.name", baseUniqueName)
  }

  override fun getEditorTabTooltipHtml(project: Project, virtualFile: VirtualFile): HtmlChunk? {
    if (!isApplicable(virtualFile)) return null
    val text = GitBundle.message("stage.vfs.editor.tab.tooltip",
                                 VcsUtil.getPresentablePath(project, virtualFile.filePath(), true, false))
    return HtmlChunk.text(text)
  }
}