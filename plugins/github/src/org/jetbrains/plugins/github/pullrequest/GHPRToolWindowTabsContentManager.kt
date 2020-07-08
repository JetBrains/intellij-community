// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.content.*
import com.intellij.util.EventDispatcher
import com.intellij.util.IJSwingUtilities
import com.intellij.util.ui.UIUtil
import com.intellij.vcsUtil.VcsImplUtil
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRToolWindowTabComponentController
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRToolWindowTabComponentFactory
import org.jetbrains.plugins.github.util.GitRemoteUrlCoordinates
import java.util.*
import javax.swing.JPanel

class GHPRToolWindowTabsContentManager(private val project: Project, private val contentManager: ContentManager) {

  private val tabDisposalEventDispatcher = EventDispatcher.create(TabDisposalListener::class.java)

  val currentTabs: Set<GHRepositoryCoordinates>
    get() = contentManager.contents.mapNotNull { it.repository }.toSet()

  init {
    contentManager.addContentManagerListener(ContentInitializer())
  }

  private inner class ContentInitializer : ContentManagerListener {

    override fun selectionChanged(event: ContentManagerEvent) {
      if (event.operation != ContentManagerEvent.ContentOperation.add) return
      val content = event.content
      if (content.getUserData(INIT_DONE_KEY) != null) return

      content.component = GHPRToolWindowTabComponentFactory(project,
                                                            content.repository ?: return,
                                                            content.remote ?: return,
                                                            content.disposer ?: return).createComponent()
      IJSwingUtilities.updateComponentTreeUI(content.component)
      content.putUserData(INIT_DONE_KEY, Any())
    }
  }

  @CalledInAwt
  internal fun addTab(repository: GHRepositoryCoordinates, remote: GitRemoteUrlCoordinates) {
    val content = createContent(repository, remote)
    contentManager.addContent(content)
    updateTabNames()
  }

  @CalledInAwt
  internal fun removeTab(repository: GHRepositoryCoordinates) {
    val content = contentManager.contents.firstOrNull { it.repository == repository } ?: return
    contentManager.removeContent(content, true)
  }

  @CalledInAwt
  internal fun focusTab(repository: GHRepositoryCoordinates, onFocused: ((GHPRToolWindowTabComponentController?) -> Unit)? = null) {
    val content = contentManager.contents.firstOrNull { it.repository == repository } ?: return
    contentManager.setSelectedContent(content, true)

    val controller = UIUtil.getClientProperty(content.component, GHPRToolWindowTabComponentController.KEY)
    onFocused?.invoke(controller)
  }

  private fun createContent(repository: GHRepositoryCoordinates,
                            remote: GitRemoteUrlCoordinates): Content {
    val disposable = Disposer.newDisposable()
    Disposer.register(disposable, Disposable {
      updateTabNames()
      tabDisposalEventDispatcher.multicaster.tabDisposed(repository)
    })

    val content = ContentFactory.SERVICE.getInstance().createContent(JPanel(null), remote.remote.name, false)
    content.isCloseable = true
    content.description = remote.url
    content.repository = repository
    content.remote = remote
    content.setDisposer(disposable)
    return content
  }

  private fun updateTabNames() {
    val contents = contentManager.contents.filter { it.remote != null }

    // prefix with root name if there are duplicate remote names
    val prefixRoot = contents.map { it.remote!!.remote }.groupBy { it.name }.values.any { it.size > 1 }
    for (content in contents) {
      val remoteUrl = content.remote!!
      if (prefixRoot) {
        val shortRootName = VcsImplUtil.getShortVcsRootName(project, remoteUrl.repository.root)
        content.displayName = "$shortRootName/${remoteUrl.remote.name}"
      }
      else {
        content.displayName = remoteUrl.remote.name
      }
    }
  }

  fun addTabDisposalEventListener(listener: TabDisposalListener) = tabDisposalEventDispatcher.addListener(listener)

  fun removeTabDisposalEventListener(listener: TabDisposalListener) = tabDisposalEventDispatcher.removeListener(listener)

  private var Content.repository
    get() = getUserData(REPOSITORY)
    set(value) {
      putUserData(REPOSITORY, value)
    }

  private var Content.remote
    get() = getUserData(REMOTE)
    set(value) {
      putUserData(REMOTE, value)
    }

  interface TabDisposalListener : EventListener {
    fun tabDisposed(repository: GHRepositoryCoordinates)
  }

  companion object {
    internal val INIT_DONE_KEY = Key<Any>("GHPR_CONTENT_INIT_DONE")
    private val REPOSITORY = Key<GHRepositoryCoordinates>("GHPR_REPOSITORY")
    private val REMOTE = Key<GitRemoteUrlCoordinates>("GHPR_REMOTE")
  }
}