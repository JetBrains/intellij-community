// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch.dashboard

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider
import com.intellij.util.NotNullFunction
import git4idea.GitVcs
import javax.swing.JComponent

class BranchesDashboardTabContentProvider(val project: Project): ChangesViewContentProvider {
  private var branchView: BranchesDashboardUi? = null

  override fun initContent(): JComponent {
    val branchView = BranchesDashboardUi(project)
    this.branchView = branchView
    return branchView.getMainComponent()
  }

  override fun disposeContent() {
    branchView?.let(Disposer::dispose)
  }

  internal class BranchesTabVisibilityPredicate : NotNullFunction<Project, Boolean> {
    override fun `fun`(project: Project): Boolean {
      if (!Registry.`is`("git.show.branches.dashboard")) return false

      val roots = ProjectLevelVcsManager.getInstance(project).allVcsRoots
      return roots.any { it.vcs?.keyInstanceMethod?.equals(GitVcs.getKey()) ?: false }
    }
  }
}
