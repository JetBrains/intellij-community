// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.changes

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangesViewModifier
import com.intellij.openapi.vcs.changes.ui.ChangesViewModelBuilder
import com.jetbrains.changeReminder.anyGitRootsForIndexing
import com.jetbrains.changeReminder.plugin.UserSettings
import com.jetbrains.changeReminder.predict.PredictionService

private class ChangeReminderChangesViewModifier(private val project: Project) : ChangesViewModifier {
  private val userSettings = service<UserSettings>()

  override fun modifyTreeModelBuilder(builder: ChangesViewModelBuilder) {
    if (userSettings.isPluginEnabled && project.anyGitRootsForIndexing()) {
      val predictionService = project.service<PredictionService>()
      val prediction = predictionService.predictionDataToDisplay
      if (prediction.predictionToDisplay.isNotEmpty()) {
        val node = ChangeReminderBrowserNode(prediction, predictionService)
        builder.insertSubtreeRoot(node)
        builder.insertFilesIntoNode(prediction.predictionToDisplay, node)
      }
    }
  }
}