// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManagerListener
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.Content
import com.intellij.util.NotNullFunction
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.commit.CommitTabTitleUpdater
import com.intellij.vcsUtil.VcsUtil
import git4idea.index.GitStageContentProvider.Companion.STAGING_AREA_TAB_NAME
import git4idea.index.ui.GitStagePanel
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.util.function.Supplier
import javax.swing.JComponent

class GitStageContentProvider(private val project: Project) : ChangesViewContentProvider {
  private var disposable: Disposable? = null

  override fun initContent(): JComponent {
    val tracker = GitStageTracker.getInstance(project)
    disposable = Disposer.newDisposable("Git Stage Content Provider")
    val gitStagePanel = GitStagePanel(tracker, isVertical(), isDiffPreviewInEditor(), disposable!!) {
      ChangesViewContentManager.getToolWindowFor(project, STAGING_AREA_TAB_NAME)?.activate(null)
    }
    setupTabTitleUpdater(tracker, gitStagePanel)
    project.messageBus.connect(disposable!!).subscribe(ChangesViewContentManagerListener.TOPIC, object : ChangesViewContentManagerListener {
      override fun toolWindowMappingChanged() = gitStagePanel.updatePanelLayout()
    })
    project.messageBus.connect(disposable!!).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      override fun stateChanged(toolWindowManager: ToolWindowManager) = gitStagePanel.updatePanelLayout()
    })

    return gitStagePanel
  }

  private fun GitStagePanel.updatePanelLayout() = updateLayout(isVertical(), isDiffPreviewInEditor())

  private fun isDiffPreviewInEditor() = ChangesViewContentManager.isCommitToolWindowShown(project)

  private fun isVertical() = ChangesViewContentManager.getToolWindowFor(project, STAGING_AREA_TAB_NAME)?.anchor?.isHorizontal == false

  private fun setupTabTitleUpdater(tracker: GitStageTracker, panel: GitStagePanel) {
    val updater = CommitTabTitleUpdater(panel.tree, STAGING_AREA_TAB_NAME) { VcsBundle.message("tab.title.commit") }
    Disposer.register(panel, updater)

    updater.pathsProvider = {
      val singleRoot = ProjectLevelVcsManager.getInstance(project).allVersionedRoots.singleOrNull()
      if (singleRoot != null) listOf(VcsUtil.getFilePath(singleRoot)) else tracker.state.changedRoots.map { VcsUtil.getFilePath(it) }
    }
    updater.start()
  }

  override fun disposeContent() {
    disposable?.let { Disposer.dispose(it) }
  }

  companion object {
    @NonNls
    val STAGING_AREA_TAB_NAME = "Staging Area"
  }
}

class GitStageContentPreloader : ChangesViewContentProvider.Preloader {
  override fun preloadTabContent(content: Content) {
    content.putUserData(ChangesViewContentManager.ORDER_WEIGHT_KEY,
                        ChangesViewContentManager.TabOrderWeight.LOCAL_CHANGES.weight + 1)
  }
}

class GitStageContentVisibilityPredicate : NotNullFunction<Project, Boolean> {
  override fun `fun`(project: Project) = isStagingAreaAvailable(project)
}

class GitStageDisplayNameSupplier : Supplier<String> {
  override fun get(): @Nls String = VcsBundle.message("tab.title.commit")
}

@RequiresEdt
fun showStagingArea(project: Project, commitMessage: String) {
  showStagingArea(project) {
    it.setCommitMessage(commitMessage)
  }
}

private fun showStagingArea(project: Project, consumer: (GitStagePanel) -> Unit) {
  val toolWindow = ChangesViewContentManager.getToolWindowFor(project, STAGING_AREA_TAB_NAME) ?: return
  toolWindow.activate({
                        val contentManager = ChangesViewContentManager.getInstance(project) as ChangesViewContentManager
                        val content = contentManager.findContents { it.tabName == STAGING_AREA_TAB_NAME }.singleOrNull() ?: return@activate

                        contentManager.setSelectedContent(content, true)
                        (content.component as? GitStagePanel)?.let(consumer)
                      }, true)
}