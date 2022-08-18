// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index.vfs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.impl.FileStatusProvider
import com.intellij.openapi.vfs.VirtualFile
import git4idea.index.GitStageTracker
import git4idea.index.isStagingAreaAvailable
import git4idea.index.status

class GitIndexVirtualFileStatusProvider(val project: Project) : FileStatusProvider {
  override fun getFileStatus(virtualFile: VirtualFile): FileStatus? {
    if (virtualFile !is GitIndexVirtualFile || !isStagingAreaAvailable(project)) return null

    return GitStageTracker.getInstance(project).status(virtualFile)?.getStagedStatus()
  }
}