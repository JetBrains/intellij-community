// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.performanceTesting

import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsShowConfirmationOption
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter

internal class VcsDisableConfirmationPopupCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "vcsDisableConfirmationPopup"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val vcsManager = ProjectLevelVcsManager.getInstance(context.project)
    val vcss = vcsManager.getAllSupportedVcss()
    vcss.forEach { vcs ->
      VcsConfiguration.StandardConfirmation.entries.forEach { confirmationType ->
        val option = vcsManager.getStandardConfirmation(confirmationType, vcs)
        option.setValue(VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY)
      }
    }
  }

  override fun getName(): String {
    return NAME
  }
}
