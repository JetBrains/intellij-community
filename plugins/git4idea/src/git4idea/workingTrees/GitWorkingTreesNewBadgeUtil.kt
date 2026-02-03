// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import com.intellij.vcs.git.workingTrees.GitWorkingTreesUtil.isWorkingTreesFeatureEnabled
import git4idea.workingTrees.GitWorkingTreesNewBadgeUtil.NUMBER_OF_PROJECTS_WITH_GIT_KEY
import git4idea.workingTrees.GitWorkingTreesNewBadgeUtil.STOP_SHOWING_NEW_BADGE_KEY

internal object GitWorkingTreesNewBadgeUtil {
  /**
   * Badges `New` are shown until an action on working trees is invoked,
   * or opening of a project with at least one configured git repo happens 5 times.
   *
   * Visibility of `New` badge is controlled by app-level property [STOP_SHOWING_NEW_BADGE_KEY].
   * Number of projects with git repos opened is controlled by app-level property [NUMBER_OF_PROJECTS_WITH_GIT_KEY].
   */
  private const val STOP_SHOWING_NEW_BADGE_KEY = "git.working.trees.stop.showing.new.badge"
  private const val NUMBER_OF_PROJECTS_WITH_GIT_KEY = "git.working.trees.number.of.projects.with.git"

  fun shouldShowBadgeNew(): Boolean {
    return isWorkingTreesFeatureEnabled() && !PropertiesComponent.getInstance().getBoolean(STOP_SHOWING_NEW_BADGE_KEY, false)
  }

  fun workingTreesFeatureWasUsed() {
    PropertiesComponent.getInstance().setValue(STOP_SHOWING_NEW_BADGE_KEY, true)
  }

  fun addLabelNewIfNeeded(presentation: com.intellij.openapi.actionSystem.Presentation) {
    if (shouldShowBadgeNew()) {
      presentation.putClientProperty(ActionUtil.SECONDARY_ICON, AllIcons.General.New_badge)
    }
  }

  fun reportProjectWithGitOpened() {
    if (!isWorkingTreesFeatureEnabled() || !shouldShowBadgeNew()) return
    val properties = PropertiesComponent.getInstance()
    val number = properties.getInt(NUMBER_OF_PROJECTS_WITH_GIT_KEY, 0)
    if (number > 4) {
      properties.setValue(STOP_SHOWING_NEW_BADGE_KEY, true)
      properties.unsetValue(NUMBER_OF_PROJECTS_WITH_GIT_KEY)
    }
    else {
      properties.setValue(NUMBER_OF_PROJECTS_WITH_GIT_KEY, number + 1, 0)
    }
  }
}

internal class GitWorkingTreeStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (ApplicationManager.getApplication().isUnitTestMode) return
    if (!GitWorkingTreesNewBadgeUtil.shouldShowBadgeNew()) return
    val holder = GitRepositoriesHolder.getInstance(project)
    holder.awaitInitialization()
    if (holder.getAll().isNotEmpty()) {
      GitWorkingTreesNewBadgeUtil.reportProjectWithGitOpened()
    }
  }
}
