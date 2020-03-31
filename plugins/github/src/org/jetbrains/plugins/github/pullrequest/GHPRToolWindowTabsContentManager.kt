// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.content.*
import com.intellij.util.IJSwingUtilities
import com.intellij.vcsUtil.VcsImplUtil
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.util.GitRemoteUrlCoordinates
import javax.swing.JPanel

class GHPRToolWindowTabsContentManager(private val project: Project, private val contentManager: ContentManager) {

  val currentTabs: Set<GitRemoteUrlCoordinates>
    get() = contentManager.contents.mapNotNull { it.remoteUrl }.toSet()

  init {
    contentManager.addContentManagerListener(ContentInitializer())
  }

  private inner class ContentInitializer : ContentManagerListener {

    override fun selectionChanged(event: ContentManagerEvent) {
      if (event.operation != ContentManagerEvent.ContentOperation.add) return
      val content = event.content
      if (content.getUserData(INIT_DONE_KEY) != null) return

      content.component = GHPRAccountsComponent(GithubAuthenticationManager.getInstance(), project,
                                                content.remoteUrl ?: return,
                                                content.disposer ?: return)
      IJSwingUtilities.updateComponentTreeUI(content.component)
      content.putUserData(INIT_DONE_KEY, Any())
    }
  }

  @CalledInAwt
  internal fun addTab(remoteUrl: GitRemoteUrlCoordinates, onDispose: Disposable) {
    val content = createContent(remoteUrl, onDispose)
    contentManager.addContent(content)
    updateTabNames()
  }

  @CalledInAwt
  internal fun removeTab(remoteUrl: GitRemoteUrlCoordinates) {
    val content = contentManager.contents.firstOrNull { it.remoteUrl == remoteUrl } ?: return
    contentManager.removeContent(content, true)
  }

  @CalledInAwt
  internal fun focusTab(remoteUrl: GitRemoteUrlCoordinates) {
    val content = contentManager.contents.firstOrNull { it.remoteUrl == remoteUrl } ?: return
    contentManager.setSelectedContent(content, true)
  }

  private fun createContent(remoteUrl: GitRemoteUrlCoordinates, onDispose: Disposable): Content {
    val disposable = Disposer.newDisposable()
    Disposer.register(disposable, Disposable { updateTabNames() })
    Disposer.register(disposable, onDispose)

    val content = ContentFactory.SERVICE.getInstance().createContent(JPanel(null), remoteUrl.remote.name, false)
    content.isCloseable = true
    content.description = remoteUrl.url
    content.remoteUrl = remoteUrl
    content.setDisposer(disposable)
    return content
  }

  private fun updateTabNames() {
    val contents = contentManager.contents.filter { it.remoteUrl != null }

    // prefix with root name if there are duplicate remote names
    val prefixRoot = contents.map { it.remoteUrl!!.remote }.groupBy { it.name }.values.any { it.size > 1 }
    for (content in contents) {
      val remoteUrl = content.remoteUrl!!
      if (prefixRoot) {
        val shortRootName = VcsImplUtil.getShortVcsRootName(project, remoteUrl.repository.root)
        content.displayName = "$shortRootName/${remoteUrl.remote.name}"
      }
      else {
        content.displayName = remoteUrl.remote.name
      }
    }
  }

  private var Content.remoteUrl
    get() = getUserData(REMOTE_URL)
    set(value) {
      putUserData(REMOTE_URL, value)
    }

  companion object {
    private val INIT_DONE_KEY = Key<Any>("GHPR_CONTENT_INIT_DONE")
    private val REMOTE_URL = Key<GitRemoteUrlCoordinates>("GHPR_REMOTE_URL")
  }
}