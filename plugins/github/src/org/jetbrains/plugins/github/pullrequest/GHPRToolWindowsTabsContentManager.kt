// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentI
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.vcsUtil.VcsImplUtil
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.util.GitRemoteUrlCoordinates
import javax.swing.JPanel

class GHPRToolWindowsTabsContentManager(private val project: Project,
                                        private val viewContentManager: ChangesViewContentI) {

  @CalledInAwt
  internal fun addTab(remoteUrl: GitRemoteUrlCoordinates, onDispose: Disposable) {
    viewContentManager.addContent(createContent(remoteUrl, onDispose))
    updateTabNames()
  }

  @CalledInAwt
  internal fun removeTab(remoteUrl: GitRemoteUrlCoordinates) {
    val content = viewContentManager.findContents { it.remoteUrl == remoteUrl }.firstOrNull() ?: return
    viewContentManager.removeContent(content)
  }

  @CalledInAwt
  fun focusTab(remoteUrl: GitRemoteUrlCoordinates) {
    val content = viewContentManager.findContents { it.remoteUrl == remoteUrl }.firstOrNull() ?: return
    ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID)?.show {
      viewContentManager.setSelectedContent(content, true)
    }
  }

  private fun createContent(remoteUrl: GitRemoteUrlCoordinates, onDispose: Disposable): Content {
    val disposable = Disposer.newDisposable().also {
      Disposer.register(it, Disposable { updateTabNames() })
      Disposer.register(it, onDispose)
    }

    return ContentFactory.SERVICE.getInstance().createContent(JPanel(null), GROUP_PREFIX, false).apply {
      isCloseable = true
      disposer = disposable
      description = remoteUrl.url
      this.remoteUrl = remoteUrl
      putUserData(ChangesViewContentManager.ORDER_WEIGHT_KEY, ChangesViewContentManager.TabOrderWeight.LAST.weight)
      putUserData(ChangesViewContentManager.CONTENT_PROVIDER_SUPPLIER_KEY) {
        object : ChangesViewContentProvider {
          override fun initContent() =
            GHPRAccountsComponent(GithubAuthenticationManager.getInstance(), project, remoteUrl, disposable)

          override fun disposeContent() = Disposer.dispose(disposable)
        }
      }
    }
  }

  private fun updateTabNames() {
    val contents = viewContentManager.findContents { it.remoteUrl != null }
    if (contents.size == 1) contents.single().displayName = GROUP_PREFIX
    else {
      // prefix with root name if there are duplicate remote names
      val prefixRoot = contents.map { it.remoteUrl!!.remote }.groupBy { it.name }.values.any { it.size > 1 }
      for (content in contents) {
        val remoteUrl = content.remoteUrl!!
        if (prefixRoot) {
          val shortRootName = VcsImplUtil.getShortVcsRootName(project, remoteUrl.repository.root)
          content.displayName = "${GROUP_PREFIX}: $shortRootName/${remoteUrl.remote.name}"
        }
        else {
          content.displayName = "${GROUP_PREFIX}: ${remoteUrl.remote.name}"
        }
      }
    }
  }

  private var Content.remoteUrl
    get() = getUserData(REMOTE_URL)
    set(value) {
      putUserData(REMOTE_URL, value)
    }

  companion object {
    @Nls
    private const val GROUP_PREFIX = "Pull Requests"

    private val REMOTE_URL = Key<GitRemoteUrlCoordinates>("GHPR_REMOTE_URL")
  }
}