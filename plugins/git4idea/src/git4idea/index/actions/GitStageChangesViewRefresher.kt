// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangesViewRefresher
import git4idea.index.vfs.GitIndexFileSystemRefresher

class GitStageChangesViewRefresher : ChangesViewRefresher {
  override fun refresh(project: Project) {
    GitIndexFileSystemRefresher.getInstance(project).refresh { true }
  }
}
