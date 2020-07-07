// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRToolWindowTabComponentController
import org.jetbrains.plugins.github.util.CollectionDelta
import org.jetbrains.plugins.github.util.GHProjectRepositoriesManager
import kotlin.properties.Delegates.observable

@Service
internal class GHPRToolWindowTabsManager(private val project: Project) : Disposable {
  private val repositoryManager = project.service<GHProjectRepositoriesManager>()
  private val settings = GithubPullRequestsProjectUISettings.getInstance(project)

  private val tabDisposalListener = object : GHPRToolWindowTabsContentManager.TabDisposalListener {

    var muted = false

    override fun tabDisposed(repository: GHRepositoryCoordinates) {
      if (!muted) {
        if (repositoryManager.knownRepositories.any { it.repository == repository }) settings.addHiddenUrl(repository.toUrl())
        updateTabs()
      }
    }
  }

  internal var contentManager: GHPRToolWindowTabsContentManager?
    by observable<GHPRToolWindowTabsContentManager?>(null) { _, oldManager, newManager ->
      oldManager?.removeTabDisposalEventListener(tabDisposalListener)
      newManager?.addTabDisposalEventListener(tabDisposalListener)
      updateTabs()
    }

  init {
    repositoryManager.addRepositoryListChangedListener(this) {
      updateTabs()
    }
  }

  @CalledInAwt
  fun isAvailable(): Boolean = getRepositories().isNotEmpty()

  @CalledInAwt
  fun showTab(repository: GHRepositoryCoordinates, onShown: ((GHPRToolWindowTabComponentController?) -> Unit)? = null) {
    settings.removeHiddenUrl(repository.toUrl())
    updateTabs {
      ToolWindowManager.getInstance(project).getToolWindow(GHPRToolWindowFactory.ID)?.show {
        contentManager?.focusTab(repository, onShown)
      }
    }
  }

  private fun updateTabs(afterUpdate: (() -> Unit)? = null) {
    val repositories = getRepositories().associateBy({ it.repository }, { it.remote })
    ToolWindowManager.getInstance(project).getToolWindow(GHPRToolWindowFactory.ID)?.setAvailable(repositories.isNotEmpty()) {
      val contentManager = contentManager
      if (contentManager != null) {
        val delta = CollectionDelta(contentManager.currentTabs, repositories.keys)
        for (item in delta.removedItems) {
          contentManager.removeTab(item)
        }
        for (item in delta.newItems) {
          contentManager.addTab(item, repositories.getValue(item))
        }
      }
      afterUpdate?.invoke()
    }
  }

  private fun getRepositories() = repositoryManager.knownRepositories.filter {
    !settings.getHiddenUrls().contains(it.repository.toUrl())
  }

  class BeforePluginUnloadListener(private val project: Project) : DynamicPluginListener {
    override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
      if (pluginDescriptor.pluginId == PluginManager.getInstance().getPluginOrPlatformByClassName(this::class.java.name)) {
        muteTabDisposalListener(project)
      }
    }
  }

  class BeforeProjectCloseListener : ProjectManagerListener {
    override fun projectClosing(project: Project) = muteTabDisposalListener(project)
  }

  companion object {
    private fun muteTabDisposalListener(project: Project) {
      project.service<GHPRToolWindowTabsManager>().tabDisposalListener.muted = true
    }
  }

  override fun dispose() {
  }
}
