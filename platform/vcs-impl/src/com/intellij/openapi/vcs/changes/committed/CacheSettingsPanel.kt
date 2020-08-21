// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.ui.layout.*

object CacheSettingsDialog {
  @JvmStatic
  fun showSettingsDialog(project: Project): Boolean =
    ShowSettingsUtil.getInstance().editConfigurable(project, CacheSettingsPanel(project))
}

private const val COLUMNS_COUNT = 6

internal class CacheSettingsPanel(project: Project) : BoundConfigurable(message("cache.settings.dialog.title")) {
  private val cache = CommittedChangesCache.getInstance(project)
  private val cacheState = CommittedChangesCacheState().apply { copyFrom(cache.state) }

  override fun apply() {
    super.apply()
    cache.loadState(cacheState)
  }

  override fun createPanel(): DialogPanel =
    panel {
      if (cache.isMaxCountSupportedForProject) countRow() else daysRow()
      row {
        val refreshCheckBox = checkBox(message("changes.refresh.changes.every"), cacheState::isRefreshEnabled).actsAsLabel()
        cell {
          intTextField(cacheState::refreshInterval, COLUMNS_COUNT, 1..60 * 24)
            .enableIf(refreshCheckBox.selected)
          label(message("changes.minutes"))
        }
      }
    }

  private fun LayoutBuilder.countRow() =
    row(message("changes.changelists.to.cache.initially")) {
      intTextField(cacheState::initialCount, COLUMNS_COUNT, 1..100000)
    }

  private fun LayoutBuilder.daysRow() =
    row(message("changes.days.of.history.to.cache.initially")) {
      intTextField(cacheState::initialDays, COLUMNS_COUNT, 1..720)
    }
}