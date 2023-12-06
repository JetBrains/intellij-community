// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui

import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.ui.components.JBPanel
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.util.VcsLogUtil
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import javax.swing.JComponent

interface VcsLogUiHolder {
  val vcsLogUi: VcsLogUiEx

  companion object {
    @JvmStatic
    fun getLogUis(c: JComponent): List<VcsLogUiEx> {
      val panels = mutableSetOf<VcsLogUiHolder>()
      collectLogPanelInstances(c, panels)
      return panels.map { it.vcsLogUi }
    }

    private fun collectLogPanelInstances(component: JComponent, result: MutableSet<in VcsLogUiHolder>) {
      if (component is VcsLogUiHolder) {
        result.add(component)
        return
      }
      for (childComponent in component.components) {
        if (childComponent is JComponent) {
          collectLogPanelInstances(childComponent, result)
        }
      }
    }
  }
}

class VcsLogPanel(private val manager: VcsLogManager, override val vcsLogUi: VcsLogUiEx) : JBPanel<VcsLogPanel>(BorderLayout()),
                                                                                           VcsLogUiHolder, DataProvider {
  init {
    add(vcsLogUi.getMainComponent(), BorderLayout.CENTER)
  }

  override fun getData(dataId: @NonNls String): Any? {
    if (VcsLogInternalDataKeys.LOG_MANAGER.`is`(dataId)) return manager
    else if (VcsLogDataKeys.VCS_LOG.`is`(dataId)) return vcsLogUi.getVcsLog()
    else if (VcsLogDataKeys.VCS_LOG_UI.`is`(dataId)) return vcsLogUi
    else if (VcsLogDataKeys.VCS_LOG_DATA_PROVIDER.`is`(dataId) || VcsLogInternalDataKeys.LOG_DATA.`is`(dataId)) return manager.dataManager
    else if (VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION.`is`(dataId)) return vcsLogUi.getTable().selection
    else if (VcsDataKeys.VCS_REVISION_NUMBER.`is`(dataId)) {
      val hashes = vcsLogUi.getTable().selection.commits
      if (hashes.isEmpty()) return null
      return VcsLogUtil.convertToRevisionNumber(hashes.first().hash)
    }
    else if (VcsDataKeys.VCS_REVISION_NUMBERS.`is`(dataId)) {
      val hashes = vcsLogUi.getTable().selection.commits
      if (hashes.size > VcsLogUtil.MAX_SELECTED_COMMITS) return null
      return hashes.map { VcsLogUtil.convertToRevisionNumber(it.hash) }.toTypedArray<VcsRevisionNumber>()
    }
    else if (VcsDataKeys.VCS_COMMIT_SUBJECTS.`is`(dataId)) {
      val metadata = vcsLogUi.getTable().selection.cachedMetadata
      if (metadata.size > VcsLogUtil.MAX_SELECTED_COMMITS) return null
      return metadata.map { it.getSubject() }.toTypedArray()
    }
    return null
  }
}