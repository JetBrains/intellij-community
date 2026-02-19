// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider
import com.intellij.ui.content.Content
import com.intellij.util.IJSwingUtilities
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.impl.VcsLogTabsManager.Companion.onDisplayNameChange
import com.intellij.vcs.log.ui.MainVcsLogUi
import com.intellij.vcs.log.ui.VcsLogPanel
import java.util.function.Predicate
import java.util.function.Supplier

/**
 * Provides the Content tab to the ChangesView log toolwindow.
 *
 * Delegates to the VcsLogManager.
 */
internal class VcsLogContentProvider(private val project: Project) : ChangesViewContentProvider {
  override fun initTabContent(content: Content) {
    val projectLog = VcsProjectLog.getInstance(project) as? IdeVcsProjectLog ?: return

    thisLogger<VcsLogContentProvider>().debug("Adding main Log ui container to the content for ${project.name}")

    content.component = AsyncProcessIcon.createBig("VCS Log initializing")
    // Display name is always used for presentation, tab name is used as an id.
    // See com.intellij.vcs.log.impl.VcsLogContentUtil.selectMainLog.
    content.tabName = VcsLogContentUtil.MAIN_LOG_TAB_NAME //NON-NLS

    projectLog.initAsync(true)
    projectLog.setMainUiHolder(ContentMainUiHolder(content))
    content.setDisposer {
      projectLog.setMainUiHolder(null)
    }
  }

  private class ContentMainUiHolder(private val content: Content) : IdeVcsProjectLog.MainUiHolder {
    override fun installMainUi(manager: IdeVcsLogManager, ui: MainVcsLogUi) {
      content.displayName = VcsLogTabsUtil.generateDisplayName(ui)
      ui.onDisplayNameChange {
        content.displayName = VcsLogTabsUtil.generateDisplayName(ui)
      }

      val panel = VcsLogPanel(manager, ui)
      content.component = panel
      IJSwingUtilities.updateComponentTreeUI(panel)
    }
  }

  internal class VcsLogVisibilityPredicate : Predicate<Project> {
    override fun test(project: Project): Boolean = VcsProjectLog.isAvailable(project)
  }

  internal class DisplayNameSupplier : Supplier<String> {
    override fun get(): String = VcsLogBundle.message("vcs.log.tab.name")
  }
}