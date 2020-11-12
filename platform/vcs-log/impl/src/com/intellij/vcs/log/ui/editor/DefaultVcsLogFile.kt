// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.editor

import com.intellij.ide.actions.SplitAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.impl.*
import com.intellij.vcs.log.ui.VcsLogPanel
import java.awt.BorderLayout
import javax.swing.JComponent

internal class DefaultVcsLogFile(name: String, internal val tabId: String, private var filters: VcsLogFilterCollection?)
  : VcsLogFile(name) {

  init {
    putUserData(SplitAction.FORBID_TAB_SPLIT, true)
  }

  override fun createMainComponent(project: Project): JComponent {
    val projectLog = VcsProjectLog.getInstance(project)
    val logManager = projectLog.logManager!!
    val tabsManager = projectLog.tabsManager

    try {
      val factory = tabsManager.getPersistentVcsLogUiFactory(logManager, tabId, VcsLogManager.LogWindowKind.EDITOR, filters)
      val ui = logManager.createLogUi(factory, VcsLogManager.LogWindowKind.EDITOR)
      ui.filterUi.addFilterListener { updateTabName(project, ui) }
      if (filters != null) filters = null
      return VcsLogPanel(logManager, ui)
    }
    catch (e: CannotAddVcsLogWindowException) {
      LOG.error(e)
      return JBPanelWithEmptyText(BorderLayout()).withEmptyText(VcsLogBundle.message("vcs.log.duplicated.tab.id.error"))
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as DefaultVcsLogFile

    if (tabId != other.tabId) return false

    return true
  }

  override fun hashCode(): Int {
    return tabId.hashCode()
  }

  companion object {
    private val LOG = logger<DefaultVcsLogFile>()
  }
}

class DefaultVcsLogFileTabTitleProvider : EditorTabTitleProvider, DumbAware {

  override fun getEditorTabTooltipText(project: Project, file: VirtualFile): String? {
    if (file !is DefaultVcsLogFile) return null
    return getEditorTabTitle(project, file)
  }

  override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
    if (file !is DefaultVcsLogFile) return null
    val ui = findVcsLogUi(project) { it.id == file.tabId } ?: return file.presentableName
    return VcsLogTabsManager.generateDisplayName(ui)
  }
}