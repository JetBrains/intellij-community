// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.stash.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.vcs.changes.savedPatches.SavedPatchesUi
import com.intellij.openapi.vcs.changes.savedPatches.ShelfProvider
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentI
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManagerListener
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.content.Content
import com.intellij.util.messages.Topic
import git4idea.config.GitVcsApplicationSettings
import git4idea.i18n.GitBundle
import git4idea.index.showToolWindowTab
import git4idea.repo.GitRepositoryManager
import git4idea.stash.GitStashTracker
import git4idea.stash.GitStashTrackerListener
import git4idea.stash.isNotEmpty
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.Component
import java.util.function.Predicate
import java.util.function.Supplier

internal class GitStashContentProvider(private val project: Project) : ChangesViewContentProvider {

  override fun initTabContent(content: Content) {
    project.service<GitStashTracker>().scheduleRefresh()

    val disposable = Disposer.newDisposable("Git Stash Content Provider")
    val savedPatchesUi = GitSavedPatchesUi(GitStashProvider(project, disposable), ShelfProvider(project, disposable), disposable)
    project.messageBus.connect(disposable).subscribe(GitStashSettingsListener.TOPIC, object : GitStashSettingsListener {
      override fun onSplitDiffPreviewSettingChanged() = savedPatchesUi.updateLayout()
    })
    project.messageBus.connect(disposable).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      override fun stateChanged(toolWindowManager: ToolWindowManager) = savedPatchesUi.updateLayout()
    })

    content.component = savedPatchesUi
    content.setDisposer(disposable)
  }

  private inner class GitSavedPatchesUi(private val stashProvider: GitStashProvider, private val shelfProvider: ShelfProvider,
                                        parentDisposable: Disposable) :
    SavedPatchesUi(project, listOf(stashProvider, shelfProvider), isVertical = ::isVertical, isWithSplitDiffPreview = ::isWithSplitDiffPreview,
                   isShowDiffWithLocal = ::isShowDiffWithLocal, focusMainUi = ::returnFocusToToolWindow, parentDisposable) {

    init {
      patchesTree.emptyText
        .appendLine("")
        .appendLine(AllIcons.General.ContextHelp, GitBundle.message("stash.empty.text.help.link"),
                    SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) {
          HelpManager.getInstance().invokeHelp("reference.VersionControl.Git.StashAndShelf")
        }
      project.messageBus.connect(this).subscribe(GitStashSettingsListener.TOPIC, object : GitStashSettingsListener {
        override fun onCombineStashAndShelveSettingChanged() {
          updateVisibleProviders()
          updateTabName()
        }
      })
      updateVisibleProviders()
    }

    private fun updateVisibleProviders() {
      val isStashesAndShelvesTabEnabled = isStashesAndShelvesTabEnabled(project)
      setVisibleProviders(if (isStashesAndShelvesTabEnabled) listOf(stashProvider, shelfProvider) else listOf(stashProvider))
    }

    private fun updateTabName() {
      val contentManager = project.serviceIfCreated<ChangesViewContentI>() ?: return
      val content = contentManager.findContent(TAB_NAME) ?: return
      content.displayName = GitStashDisplayNameSupplier(project).get()
    }
  }

  private fun isVertical() = isStashTabVertical(project)

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

  private fun isShowDiffWithLocal(): Boolean {
    return GitVcsApplicationSettings.getInstance().isCompareWithLocalInStashesEnabled
  }

  private fun isWithSplitDiffPreview(): Boolean {
    return GitVcsApplicationSettings.getInstance().isSplitDiffPreviewInStashesEnabled
  }

  companion object {
    @NonNls
    const val TAB_NAME = "Stash"
  }
}

internal class GitStashContentPreloader(val project: Project) : ChangesViewContentProvider.Preloader {
  override fun preloadTabContent(content: Content) {
    content.putUserData(ChangesViewContentManager.ORDER_WEIGHT_KEY, ChangesViewContentManager.TabOrderWeight.SHELF.weight + 1)
  }
}

internal class GitStashContentVisibilityPredicate : Predicate<Project> {
  override fun test(project: Project) = isStashTabVisible(project)
}

internal class GitStashDisplayNameSupplier(private val project: Project) : Supplier<String> {
  override fun get(): @Nls String {
    if (isStashesAndShelvesTabEnabled(project)) {
      return GitBundle.message("stashes.and.shelves.tab.name")
    }
    return GitBundle.message("stash.tab.name")
  }
}

internal class GitStashStartupActivity : ProjectActivity {
  init {
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode || app.isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
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
    project.messageBus.connect(gitStashTracker).subscribe(GitStashSettingsListener.TOPIC, object : GitStashSettingsListener {
      override fun onCombineStashAndShelveSettingChanged() {
        project.messageBus.syncPublisher(ChangesViewContentManagerListener.TOPIC).toolWindowMappingChanged()
      }
    })
  }
}

interface GitStashSettingsListener {
  fun onCombineStashAndShelveSettingChanged() = Unit
  fun onSplitDiffPreviewSettingChanged() = Unit

  companion object {
    val TOPIC: Topic<GitStashSettingsListener> = Topic(GitStashSettingsListener::class.java)
  }
}

internal fun stashToolWindowRegistryOption(): RegistryValue = Registry.get("git.enable.stash.toolwindow")
internal fun isStashTabAvailable(): Boolean = stashToolWindowRegistryOption().asBoolean()
internal fun isStashTabVisible(project: Project): Boolean {
  if (!isStashTabAvailable()) return false
  return isStashesAndShelvesTabEnabled(project) || project.service<GitStashTracker>().isNotEmpty()
}

internal fun isStashesAndShelvesTabEnabled(project: Project): Boolean {
  return ShelvedChangesViewManager.hideDefaultShelfTab(project)
}

internal fun setStashesAndShelvesTabEnabled(enabled: Boolean) {
  val applicationSettings = GitVcsApplicationSettings.getInstance()
  if (enabled == applicationSettings.isCombinedStashesAndShelvesTabEnabled) return

  applicationSettings.isCombinedStashesAndShelvesTabEnabled = enabled

  ApplicationManager.getApplication().messageBus.syncPublisher(GitStashSettingsListener.TOPIC).onCombineStashAndShelveSettingChanged()
}

@JvmOverloads
internal fun showStashes(project: Project, root: VirtualFile? = null) {
  val repository = root?.let { GitRepositoryManager.getInstance(project).getRepositoryForRootQuick(root) }
  showToolWindowTab(project, GitStashContentProvider.TAB_NAME) { component ->
    val savedPatchesUi = component as? SavedPatchesUi ?: return@showToolWindowTab
    val provider = savedPatchesUi.providers.filterIsInstance<GitStashProvider>().firstOrNull() ?: return@showToolWindowTab
    if (repository == null) {
      savedPatchesUi.showFirstUnderProvider(provider)
    } else {
      savedPatchesUi.showFirstUnderObject(provider, repository)
    }
  }
}

internal fun isStashTabVertical(project: Project): Boolean {
  return ChangesViewContentManager.getToolWindowFor(project, GitStashContentProvider.TAB_NAME)?.anchor?.isHorizontal == false
}