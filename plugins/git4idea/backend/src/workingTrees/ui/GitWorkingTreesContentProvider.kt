// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.ui

import com.intellij.collaboration.async.cancelledWith
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.components.Badge
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import git4idea.GitDisposable
import git4idea.i18n.GitBundle
import git4idea.workingTrees.GitWorkingTreesNewBadgeUtil
import git4idea.workingTrees.GitWorkingTreesService
import java.awt.ComponentOrientation
import java.util.function.Predicate

internal class GitWorkingTreesContentProvider(private val project: Project) : ChangesViewContentProvider {

  companion object {
    //registered with com.intellij.statistics.actionCustomPlaceAllowlist ExtensionPoint
    internal const val GIT_WORKING_TREE_TOOLWINDOW_TAB_TOOLBAR: String = "GitWorkingTreeToolWindowTabToolbar"
    internal const val GIT_WORKING_TREE_TOOLWINDOW_TAB_EMPTY_LIST: String = "GitWorkingTreeToolWindowTabEmptyList"

    internal const val EMPTY_TAB_WORKING_TREE_CONCEPT_HELP_ID = "worktree-concept"
    internal const val TOOLWINDOW_CONTENT_HELP_ID = "worktree-help"
  }

  override fun initTabContent(content: Content) {
    val disposable = Disposer.newDisposable()
    content.setDisposer(disposable)
    val cs = GitDisposable.getInstance(project).childScope("GitWorktreesTab").cancelledWith(disposable)
    content.component = GitWorktreesTabPanel(project, cs).component
  }
}

internal class GitWorkingTreesContentPreloader(val project: Project) : ChangesViewContentProvider.Preloader {
  override fun preloadTabContent(content: Content) {
    content.putUserData(ChangesViewContentManager.ORDER_WEIGHT_KEY, ChangesViewContentManager.TabOrderWeight.WORKING_TREES.weight)

    content.apply {
      isCloseable = true
      displayName = GitBundle.message("toolwindow.working.trees.tab.name")
      if (GitWorkingTreesNewBadgeUtil.shouldShowBadgeNew()) {
        icon = Badge.new
        putUserData(ToolWindow.SHOW_CONTENT_ICON, true)
        putUserData(ToolWindowContentUi.NOT_SELECTED_TAB_ICON_TRANSPARENT, false)
        putUserData(Content.TAB_LABEL_ORIENTATION_KEY, ComponentOrientation.RIGHT_TO_LEFT)
      }
    }
    // content.manager is not yet initialized here
    val contentManager = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.VCS)?.contentManager
    contentManager?.addContentManagerListener(object : ContentManagerListener {
      override fun contentRemoved(event: ContentManagerEvent) {
        if (event.content == content) {
          GitWorkingTreesService.getInstance(project).workingTreesTabClosedByUser()
          // Stop listening once our own content is gone, so listeners don't pile up across tab reopens.
          contentManager.removeContentManagerListener(this)
        }
      }
    })
  }
}

internal class GitWorkingTreesContentVisibilityPredicate : Predicate<Project> {
  override fun test(project: Project): Boolean = GitWorkingTreesService.getInstance(project).shouldWorkingTreesTabBeShown()
}
