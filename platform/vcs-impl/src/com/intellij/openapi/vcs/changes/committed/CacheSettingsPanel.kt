// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.ui.dsl.builder.*

object CacheSettingsDialog {
  @JvmStatic
  fun showSettingsDialog(project: Project): Boolean =
    ShowSettingsUtil.getInstance().editConfigurable(project, CacheSettingsPanel(project))
}

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
        val refreshCheckBox = checkBox(message("changes.refresh.changes.every"))
          .bindSelected(cacheState::isRefreshEnabled)
        intTextField(1..60 * 24)
          .bindIntText(cacheState::refreshInterval)
          .enabledIf(refreshCheckBox.selected)
          .gap(RightGap.SMALL)
        label(message("changes.minutes"))
      }.layout(RowLayout.PARENT_GRID)
    }

  private fun Panel.countRow() =
    row(message("changes.changelists.to.cache.initially")) {
      intTextField(1..100000)
        .bindIntText(cacheState::initialCount)
    }

  private fun Panel.daysRow() =
    row(message("changes.days.of.history.to.cache.initially")) {
      intTextField(1..720)
        .bindIntText(cacheState::initialDays)
    }
}
