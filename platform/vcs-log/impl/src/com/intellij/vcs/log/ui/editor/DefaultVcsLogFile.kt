// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.editor

import com.intellij.ide.actions.SplitAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.impl.VcsLogContentUtil
import com.intellij.vcs.log.impl.VcsLogTabsManager
import com.intellij.vcs.log.ui.VcsLogPanel
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

internal class DefaultVcsLogFile(name: String, panel: VcsLogPanel) : VcsLogFile(name) {
  private var vcsLogPanel: VcsLogPanel? = null

  init {
    vcsLogPanel = panel
    Disposer.register(panel.getUi(), Disposable { vcsLogPanel = null })

    putUserData(SplitAction.FORBID_TAB_SPLIT, true)
  }

  override fun createMainComponent(project: Project): JComponent {
    return vcsLogPanel ?: JBPanelWithEmptyText().withEmptyText(VcsLogBundle.message("vcs.log.tab.closed.status"))
  }

  @Nls
  fun getDisplayName(): String? {
    return vcsLogPanel?.let {
      val logUi = VcsLogContentUtil.getLogUi(it) ?: return null
      return VcsLogTabsManager.generateDisplayName(logUi)
    }
  }

  override fun isValid(): Boolean = vcsLogPanel != null
}

class DefaultVcsLogFileTabTitleProvider : EditorTabTitleProvider {

  override fun getEditorTabTooltipText(project: Project, file: VirtualFile): String? {
    if (file !is DefaultVcsLogFile) return null
    return getEditorTabTitle(project, file)
  }

  override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
    if (file !is DefaultVcsLogFile) return null
    return file.getDisplayName() ?: file.presentableName
  }
}