// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch.dashboard

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.impl.CustomVcsLogUiFactoryProvider
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.ui.MainVcsLogUi
import git4idea.GitVcs

class BranchesInGitLogUiFactoryProvider(private val project: Project) : CustomVcsLogUiFactoryProvider {

  override fun isActive(vcsLogManager: VcsLogManager) = hasGitRoots(project, vcsLogManager)

  override fun createLogUiFactory(logId: String,
                                  vcsLogManager: VcsLogManager,
                                  filters: VcsLogFilterCollection?): VcsLogManager.VcsLogUiFactory<out MainVcsLogUi> =
    BranchesVcsLogUiFactory(vcsLogManager, logId, filters)

  private fun hasGitRoots(project: Project, logManager: VcsLogManager) =
    ProjectLevelVcsManager.getInstance(project).allVcsRoots.asSequence()
      .filter { it.vcs?.keyInstanceMethod == GitVcs.getKey() }
      .map(VcsRoot::getPath)
      .toSet()
      .containsAll(logManager.dataManager.roots)
}