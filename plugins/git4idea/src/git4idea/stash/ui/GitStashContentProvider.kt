// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.vcs.changes.savedPatches.SavedPatchesUi
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManagerListener
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.Content
import git4idea.i18n.GitBundle
import git4idea.stash.*
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.Component
import java.util.function.Predicate
import java.util.function.Supplier
import javax.swing.JComponent

internal class GitStashContentProvider(private val project: Project) : ChangesViewContentProvider {
  private var disposable: Disposable? = null

  override fun initContent(): JComponent {
    project.service<GitStashTracker>().scheduleRefresh()

    disposable = Disposer.newDisposable("Git Stash Content Provider")
    val savedPatchesUi = SavedPatchesUi(project, listOf(GitStashProvider(project)), isVertical(), isEditorDiffPreview(),
                                        ::returnFocusToToolWindow, disposable!!)
    project.messageBus.connect(disposable!!).subscribe(ChangesViewContentManagerListener.TOPIC, object : ChangesViewContentManagerListener {
      override fun toolWindowMappingChanged() {
        savedPatchesUi.updateLayout(isVertical(), isEditorDiffPreview())
      }
    })
    project.messageBus.connect(disposable!!).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      override fun stateChanged(toolWindowManager: ToolWindowManager) = savedPatchesUi.updateLayout(isVertical(), isEditorDiffPreview())
    })
    return savedPatchesUi
  }

  private fun isVertical() = ChangesViewContentManager.getToolWindowFor(project, TAB_NAME)?.anchor?.isHorizontal == false

  private fun isEditorDiffPreview() = ChangesViewContentManager.isCommitToolWindowShown(project)

  private fun returnFocusToToolWindow(componentToFocus: Component?) {
    val toolWindow = ChangesViewContentManager.getToolWindowFor(project, TAB_NAME) ?: return

    if (componentToFocus == null) {
      toolWindow.activate(null)
      return
    }

    toolWindow.activate({
                          IdeFocusManager.getInstance(project).requestFocus(componentToFocus, true)
                        }, false)
  }

  override fun disposeContent() {
    disposable?.let { Disposer.dispose(it) }
  }

  companion object {
    @NonNls
    val TAB_NAME = "Stash"
  }
}

internal class GitStashContentPreloader(val project: Project) : ChangesViewContentProvider.Preloader {
  override fun preloadTabContent(content: Content) {
    content.putUserData(ChangesViewContentManager.ORDER_WEIGHT_KEY, ChangesViewContentManager.TabOrderWeight.SHELF.weight + 1)
  }
}

internal class GitStashContentVisibilityPredicate : Predicate<Project> {
  override fun test(project: Project) = isStashToolWindowAvailable(project)
}

internal class GitStashDisplayNameSupplier : Supplier<String> {
  override fun get(): @Nls String {
    return GitBundle.message("stash.tab.name")
  }
}

internal class GitStashStartupActivity : StartupActivity.DumbAware {
  init {
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode || app.isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun runActivity(project: Project) {
    ApplicationManager.getApplication().invokeLater({
        val gitStashTracker = project.service<GitStashTracker>()
        val stashTrackerIsNotEmpty = gitStashTracker.isNotEmpty()
        gitStashTracker.addListener(object : GitStashTrackerListener {
          private var hasStashes = stashTrackerIsNotEmpty
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
        if (stashTrackerIsNotEmpty) {
          project.messageBus.syncPublisher(ChangesViewContentManagerListener.TOPIC).toolWindowMappingChanged()
        }
      }) { project.isDisposed }
  }
}
