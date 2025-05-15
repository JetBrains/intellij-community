// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider
import com.intellij.ui.content.Content
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.impl.VcsProjectLog.Companion.getLogProviders
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.function.Predicate
import java.util.function.Supplier

/**
 * Provides the Content tab to the ChangesView log toolwindow.
 *
 * Delegates to the VcsLogManager.
 */
internal class VcsLogContentProvider(private val project: Project) : ChangesViewContentProvider {
  private val projectLog = VcsProjectLog.getInstance(project)

  override fun initTabContent(content: Content) {
    if (projectLog.isDisposing) return

    thisLogger<VcsLogContentProvider>().debug("Adding main Log ui container to the content for ${project.name}")

    content.component = AsyncProcessIcon.createBig("VCS Log initializing")
    // Display name is always used for presentation, tab name is used as an id.
    // See com.intellij.vcs.log.impl.VcsLogContentUtil.selectMainLog.
    content.tabName = VcsLogContentUtil.MAIN_LOG_TAB_NAME //NON-NLS

    projectLog.createLogInBackground(true)

    val contentHolder = project.service<ContentHolder>()
    contentHolder.contentState.value = content
    content.setDisposer {
      contentHolder.contentState.value = null
    }
  }

  // can probably replace this with a listener on tw/content, but this way is just easier
  @Service(Service.Level.PROJECT)
  internal class ContentHolder {
    val contentState = MutableStateFlow<Content?>(null)
  }

  internal class VcsLogVisibilityPredicate : Predicate<Project> {
    override fun test(project: Project): Boolean {
      return !getLogProviders(project).isEmpty()
    }
  }

  internal class DisplayNameSupplier : Supplier<String> {
    override fun get(): String = VcsLogBundle.message("vcs.log.tab.name")
  }
}