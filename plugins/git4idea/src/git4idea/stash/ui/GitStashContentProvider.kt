// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.stash.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManagerListener
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider
import com.intellij.ui.content.Content
import com.intellij.util.NotNullFunction
import com.intellij.vcs.log.runInEdt
import git4idea.i18n.GitBundle
import git4idea.stash.*
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.util.function.Supplier
import javax.swing.JComponent

class GitStashContentProvider(private val project: Project) : ChangesViewContentProvider {
  private var disposable: Disposable? = null

  override fun initContent(): JComponent {
    project.service<GitStashTracker>().scheduleRefresh()

    disposable = Disposer.newDisposable("Git Stash Content Provider")
    val gitStashUi = GitStashUi(project, ChangesViewContentManager.isCommitToolWindowShown(project), disposable!!)
    project.messageBus.connect(disposable!!).subscribe(ChangesViewContentManagerListener.TOPIC, object : ChangesViewContentManagerListener {
      override fun toolWindowMappingChanged() {
        gitStashUi.setDiffPreviewInEditor(ChangesViewContentManager.isCommitToolWindowShown(project))
      }
    })
    return gitStashUi.mainComponent
  }

  override fun disposeContent() {
    disposable?.let { Disposer.dispose(it) }
  }

  companion object {
    @NonNls
    val TAB_NAME = "Stash"
  }
}

class GitStashContentPreloader(val project: Project) : ChangesViewContentProvider.Preloader {
  override fun preloadTabContent(content: Content) {
    content.putUserData(ChangesViewContentManager.ORDER_WEIGHT_KEY, ChangesViewContentManager.TabOrderWeight.SHELF.weight + 1)
  }
}

class GitStashContentVisibilityPredicate : NotNullFunction<Project, Boolean> {
  override fun `fun`(project: Project) = isStashToolWindowAvailable(project)
}

class GitStashDisplayNameSupplier : Supplier<String> {
  override fun get(): @Nls String {
    return GitBundle.message("stash.tab.name")
  }
}

class GitStashStartupActivity : StartupActivity.DumbAware {
  init {
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode || app.isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.INSTANCE
    }
  }

  override fun runActivity(project: Project) {
    runInEdt(project) {
      val gitStashTracker = project.service<GitStashTracker>()
      gitStashTracker.addListener(object : GitStashTrackerListener {
        private var hasStashes = gitStashTracker.isNotEmpty()
        override fun stashesUpdated() {
          if (hasStashes != gitStashTracker.isNotEmpty()) {
            hasStashes = gitStashTracker.isNotEmpty()
            project.messageBus.syncPublisher(ChangesViewContentManagerListener.TOPIC).toolWindowMappingChanged()
          }
        }
      }, gitStashTracker)
      stashToolWindowRegistryOption().addListener(object : RegistryValueListener {
        override fun afterValueChanged(value: RegistryValue) {
          gitStashTracker.scheduleRefresh()
          project.messageBus.syncPublisher(ChangesViewContentManagerListener.TOPIC).toolWindowMappingChanged()
        }
      }, gitStashTracker)
    }
  }
}