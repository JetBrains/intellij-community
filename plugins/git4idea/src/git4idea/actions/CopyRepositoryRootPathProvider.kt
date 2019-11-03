// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions

import com.intellij.ide.actions.CopyPathProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import git4idea.repo.GitRepositoryManager

class CopyPathFromRepositoryRootProvider : CopyPathProvider() {
  override fun getPathToElement(project: Project, virtualFile: VirtualFile?, editor: Editor?): String? {
    if (virtualFile == null) return null

    val repository = GitRepositoryManager.getInstance(project).getRepositoryForFile(virtualFile)
    if (repository == null) return null

    return VfsUtilCore.getRelativePath(virtualFile, repository.root)
  }
}