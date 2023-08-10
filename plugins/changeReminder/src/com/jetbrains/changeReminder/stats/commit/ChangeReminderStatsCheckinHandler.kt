// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.changeReminder.stats.commit

import com.intellij.openapi.components.service
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.VcsCheckinHandlerFactory
import com.jetbrains.changeReminder.predict.PredictionService
import com.jetbrains.changeReminder.stats.ChangeReminderChangesCommittedEvent
import git4idea.GitVcs

class ChangeReminderStatsCheckinHandler : VcsCheckinHandlerFactory(GitVcs.getKey()) {
  override fun createVcsHandler(panel: CheckinProjectPanel, commitContext: CommitContext) = object : CheckinHandler() {
    override fun beforeCheckin(): ReturnResult {
      val project = panel.project
      val prediction = project.service<PredictionService>().predictionDataToDisplay
      val committedFiles = panel.selectedChanges.map { ChangesUtil.getFilePath(it) }

      val curFiles = ChangeListManager.getInstance(project).defaultChangeList.changes.map { ChangesUtil.getFilePath(it) }
      ChangeReminderChangesCommittedEvent(curFiles, committedFiles, prediction).logEvent(project)
      return ReturnResult.COMMIT
    }
  }
}