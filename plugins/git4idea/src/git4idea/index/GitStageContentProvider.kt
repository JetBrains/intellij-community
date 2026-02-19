// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider
import com.intellij.openapi.vcs.changes.ui.subscribeOnVcsToolWindowLayoutChanges
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.commit.ChangesViewCommitTabTitleUpdater
import git4idea.index.GitStageContentProvider.Companion.STAGING_AREA_TAB_NAME
import git4idea.index.ui.GitStagePanel
import org.jetbrains.annotations.NonNls
import java.util.function.Predicate
import javax.swing.JComponent

internal class GitStageContentProvider(private val project: Project) : ChangesViewContentProvider {

  override fun initTabContent(content: Content) {
    val disposable = Disposer.newDisposable("Git Stage Content Provider")
    val tracker = GitStageTracker.getInstance(project)
    val gitStagePanel = GitStagePanel(tracker, isVertical = ::isVertical, disposable) {
      ChangesViewContentManager.getToolWindowFor(project, STAGING_AREA_TAB_NAME)?.activate(null)
    }

    val busConnection = project.messageBus.connect(disposable)
    busConnection.subscribeOnVcsToolWindowLayoutChanges { gitStagePanel.updateLayout() }

    content.component = gitStagePanel
    content.setDisposer(disposable)
  }

  private fun isVertical() = ChangesViewContentManager.isToolWindowTabVertical(project, STAGING_AREA_TAB_NAME)

  companion object {
    @NonNls
    const val STAGING_AREA_TAB_NAME = "Staging Area"
  }
}

internal class GitStageContentPreloader(private val project: Project) : ChangesViewContentProvider.Preloader {
  override fun preloadTabContent(content: Content) {
    ChangesViewCommitTabTitleUpdater(project, STAGING_AREA_TAB_NAME).init(content)
    content.putUserData(ChangesViewContentManager.ORDER_WEIGHT_KEY,
                        ChangesViewContentManager.TabOrderWeight.LOCAL_CHANGES.weight + 1)
  }
}

internal class GitStageContentVisibilityPredicate : Predicate<Project> {
  override fun test(project: Project) = isStagingAreaAvailable(project)
}

@RequiresEdt
fun showStagingArea(project: Project, commitMessage: String) {
  showStagingArea(project) {
    it.commitMessage.setCommitMessage(commitMessage)
  }
}

internal fun showStagingArea(project: Project, consumer: (GitStagePanel) -> Unit) {
  showToolWindowTab(project, STAGING_AREA_TAB_NAME) { (it as? GitStagePanel)?.let(consumer) }
}

internal fun showToolWindowTab(project: Project, tabName: String, contentConsumer: (JComponent) -> Unit) {
  ToolWindowManager.getInstance(project).invokeLater {
    val toolWindow = ChangesViewContentManager.getToolWindowFor(project, tabName) ?: return@invokeLater
    toolWindow.activate({
                          val contentManager = ChangesViewContentManager.getInstance(project) as ChangesViewContentManager
                          val content = contentManager.findContent(tabName) ?: return@activate

                          contentManager.setSelectedContent(content, true)
                          contentConsumer(content.component)
                        }, true)
  }
}