// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManagerListener
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider
import com.intellij.ui.content.Content
import com.intellij.util.NotNullFunction
import git4idea.i18n.GitBundle
import git4idea.index.ui.GitStagePanel
import org.jetbrains.annotations.Nls
import java.util.function.Supplier
import javax.swing.JComponent

class GitStageContentProvider(private val project: Project) : ChangesViewContentProvider {
  private var disposable: Disposable? = null

  override fun initContent(): JComponent {
    val tracker = GitStageTracker.getInstance(project)
    disposable = Disposer.newDisposable("Git Stage Content Provider")
    val gitStagePanel = GitStagePanel(tracker, isCommitToolWindow(project), disposable!!)
    project.messageBus.connect(disposable!!).subscribe(ChangesViewContentManagerListener.TOPIC, object : ChangesViewContentManagerListener {
      override fun toolWindowMappingChanged() {
        gitStagePanel.setDiffPreviewInEditor(isCommitToolWindow(project))
      }
    })
    return gitStagePanel
  }

  override fun disposeContent() {
    disposable?.let { Disposer.dispose(it) }
  }
}

class GitStageContentPreloader : ChangesViewContentProvider.Preloader {
  override fun preloadTabContent(content: Content) {
    content.putUserData(ChangesViewContentManager.ORDER_WEIGHT_KEY,
                        ChangesViewContentManager.TabOrderWeight.LOCAL_CHANGES.weight + 1)
  }
}

class GitStageContentVisibilityPredicate : NotNullFunction<Project, Boolean> {
  override fun `fun`(project: Project) = isStageAvailable(project)
}

class GitStageDisplayNameSupplier : Supplier<String> {
  override fun get(): @Nls String {
    return GitBundle.message("stage.tab.name")
  }
}

private fun isCommitToolWindow(project: Project) = ChangesViewContentManager.getInstanceImpl(project)?.isCommitToolWindow == true