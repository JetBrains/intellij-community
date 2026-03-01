// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui

import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.ui.components.JBPanel
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.util.VcsLogUtil
import org.jetbrains.annotations.ApiStatus
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

class VcsLogPanel(private val manager: VcsLogManager, override val vcsLogUi: VcsLogUiEx)
  : JBPanel<VcsLogPanel>(BorderLayout()), VcsLogUiHolder, UiDataProvider {
  init {
    add(vcsLogUi.getMainComponent(), BorderLayout.CENTER)
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[VcsLogInternalDataKeys.LOG_MANAGER] = manager
    collectLogUiKeys(sink, vcsLogUi)
  }

  @ApiStatus.Internal
  companion object {
    fun collectLogUiKeys(
      sink: DataSink,
      vcsLogUi: VcsLogUiEx,
    ) {
      val logData = vcsLogUi.logData
      sink[VcsLogDataKeys.VCS_LOG_DATA_PROVIDER] = logData
      sink[VcsLogInternalDataKeys.LOG_DATA] = logData
      sink[VcsLogDataKeys.VCS_LOG] = vcsLogUi.vcsLog
      sink[VcsLogDataKeys.VCS_LOG_UI] = vcsLogUi

      if (!logData.isDisposed) {
        val selection = vcsLogUi.table.selection
        sink[VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION] = selection

        val hashes = selection.commits
        if (hashes.isNotEmpty()) {
          sink[VcsDataKeys.VCS_REVISION_NUMBER] = VcsLogUtil.convertToRevisionNumber(hashes.first().hash)
        }
        if (hashes.isNotEmpty() && hashes.size <= VcsLogUtil.MAX_SELECTED_COMMITS) {
          sink[PlatformDataKeys.SELECTED_ITEMS] = hashes.toTypedArray()
          sink[VcsDataKeys.VCS_REVISION_NUMBERS] = hashes
            .map { VcsLogUtil.convertToRevisionNumber(it.hash) }
            .toTypedArray<VcsRevisionNumber>()
        }
        val metadata = selection.cachedMetadata
        if (metadata.isNotEmpty() && metadata.size <= VcsLogUtil.MAX_SELECTED_COMMITS) {
          sink[VcsDataKeys.VCS_COMMIT_SUBJECTS] = metadata.map { it.getSubject() }.toTypedArray()
        }
      }
    }
  }
}