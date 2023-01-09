// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManagerListener
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.Content
import com.intellij.util.concurrency.annotations.RequiresEdt
import git4idea.index.GitStageContentProvider.Companion.STAGING_AREA_TAB_NAME
import git4idea.index.ui.GitStagePanel
import git4idea.index.ui.SimpleTabTitleUpdater
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.util.function.Predicate
import java.util.function.Supplier
import javax.swing.JComponent

internal class GitStageContentProvider(private val project: Project) : ChangesViewContentProvider {
  private var disposable: Disposable? = null

  override fun initContent(): JComponent {
    val tracker = GitStageTracker.getInstance(project)
    disposable = Disposer.newDisposable("Git Stage Content Provider")
    val gitStagePanel = GitStagePanel(tracker, isVertical = ::isVertical, isEditorDiffPreview = ::isDiffPreviewInEditor, disposable!!) {
      ChangesViewContentManager.getToolWindowFor(project, STAGING_AREA_TAB_NAME)?.activate(null)
    }
    GitStageTabTitleUpdater(tracker, gitStagePanel)
    project.messageBus.connect(disposable!!).subscribe(ChangesViewContentManagerListener.TOPIC, object : ChangesViewContentManagerListener {
      override fun toolWindowMappingChanged() = gitStagePanel.updateLayout()
    })
    project.messageBus.connect(disposable!!).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      override fun stateChanged(toolWindowManager: ToolWindowManager) = gitStagePanel.updateLayout()
    })

    return gitStagePanel
  }

  private fun isDiffPreviewInEditor() = ChangesViewContentManager.isCommitToolWindowShown(project)

  private fun isVertical() = ChangesViewContentManager.getToolWindowFor(project, STAGING_AREA_TAB_NAME)?.anchor?.isHorizontal == false

  override fun disposeContent() {
    disposable?.let { Disposer.dispose(it) }
  }

  companion object {
    @NonNls
    val STAGING_AREA_TAB_NAME = "Staging Area"
  }
}

private class GitStageTabTitleUpdater(private val tracker: GitStageTracker, panel: GitStagePanel) :
  SimpleTabTitleUpdater(panel.tree, STAGING_AREA_TAB_NAME) {

  init {
    tracker.addListener(object : GitStageTrackerListener {
      override fun update() {
        refresh()
      }
    }, panel)
    Disposer.register(panel, this)
  }

  override fun shouldShowBranches(): Boolean {
    return tracker.state.allRoots.size == 1 || super.shouldShowBranches()
  }

  override fun getRoots(): Collection<VirtualFile> {
    val roots = tracker.state.allRoots
    if (roots.size == 1) return roots
    return tracker.state.changedRoots
  }
}

class GitStageContentPreloader : ChangesViewContentProvider.Preloader {
  override fun preloadTabContent(content: Content) {
    content.putUserData(ChangesViewContentManager.ORDER_WEIGHT_KEY,
                        ChangesViewContentManager.TabOrderWeight.LOCAL_CHANGES.weight + 1)
  }
}

internal class GitStageContentVisibilityPredicate : Predicate<Project> {
  override fun test(project: Project) = isStagingAreaAvailable(project)
}

internal class GitStageDisplayNameSupplier : Supplier<String> {
  override fun get(): @Nls String = VcsBundle.message("tab.title.commit")
}

@RequiresEdt
fun showStagingArea(project: Project, commitMessage: String) {
  showStagingArea(project) {
    it.commitMessage.setCommitMessage(commitMessage)
  }
}

internal fun showStagingArea(project: Project, consumer: (GitStagePanel) -> Unit) {
  val toolWindow = ChangesViewContentManager.getToolWindowFor(project, STAGING_AREA_TAB_NAME) ?: return
  toolWindow.activate({
                        val contentManager = ChangesViewContentManager.getInstance(project) as ChangesViewContentManager
                        val content = contentManager.findContents { it.tabName == STAGING_AREA_TAB_NAME }.singleOrNull() ?: return@activate

                        contentManager.setSelectedContent(content, true)
                        (content.component as? GitStagePanel)?.let(consumer)
                      }, true)
}