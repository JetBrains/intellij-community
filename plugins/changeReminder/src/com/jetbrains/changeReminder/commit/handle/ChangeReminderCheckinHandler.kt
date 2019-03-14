// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.commit.handle

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.ui.BooleanCommitOption
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PairConsumer
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.index.IndexDataGetter
import com.intellij.vcs.log.util.VcsLogUtil.findBranch
import com.jetbrains.changeReminder.*
import com.jetbrains.changeReminder.commit.handle.ui.ChangeReminderDialog
import com.jetbrains.changeReminder.plugin.UserSettings
import com.jetbrains.changeReminder.predict.PredictedFile
import com.jetbrains.changeReminder.predict.PredictionProvider
import com.jetbrains.changeReminder.repository.FilesHistoryProvider
import com.jetbrains.changeReminder.stats.ChangeReminderData
import com.jetbrains.changeReminder.stats.ChangeReminderEvent
import com.jetbrains.changeReminder.stats.logEvent
import java.util.function.Consumer
import kotlin.system.measureTimeMillis

class ChangeReminderCheckinHandler(private val panel: CheckinProjectPanel,
                                   private val dataManager: VcsLogData,
                                   private val dataGetter: IndexDataGetter) : CheckinHandler() {
  companion object {
    private val LOG = Logger.getInstance(ChangeReminderCheckinHandler::class.java)
  }

  private val project: Project = panel.project
  private val changeListManager = ChangeListManager.getInstance(project)
  private val userSettings = ServiceManager.getService(UserSettings::class.java)

  override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent {
    return BooleanCommitOption(
      panel,
      "Predict forgotten files",
      true,
      { userSettings.isTurnedOn },
      Consumer { userSettings.isTurnedOn = it }
    )
  }


  private fun getPredictedFiles(files: Collection<FilePath>, root: VirtualFile, isAmend: Boolean, threshold: Double): List<PredictedFile> {
    val repository = FilesHistoryProvider(project, root, dataGetter)
    val filesSet = files.toMutableSet()
    if (isAmend) {
      val ref = findBranch(dataManager.dataPack.refsModel, root, "HEAD")
      if (ref != null) {
        val hash = ref.commitHash.asString()
        processCommitsFromHashes(project, root, listOf(hash)) { commit ->
          filesSet.addAll(commit.changedFilePaths())
        }
      }
    }
    if (filesSet.size > 25) {
      return emptyList()
    }

    return PredictionProvider(minProb = threshold)
      .predictForgottenFiles(repository.getFilesHistory(filesSet))
      .toPredictedFiles(changeListManager)
  }

  private fun getPredictedFiles(rootFiles: Map<VirtualFile, Collection<FilePath>>,
                                isAmend: Boolean,
                                threshold: Double): List<PredictedFile> =
    rootFiles.mapNotNull { (root, files) ->
      if (dataManager.index.isIndexed(root)) {
        getPredictedFiles(files, root, isAmend, threshold)
      }
      else {
        null
      }
    }.flatten()

  override fun beforeCheckin(executor: CommitExecutor?, additionalDataConsumer: PairConsumer<Any, Any>?): ReturnResult {
    try {
      val rootFiles = panel.getGitRootFiles(project)
      if (!userSettings.isTurnedOn || rootFiles.size > 25) {
        logEvent(project, ChangeReminderEvent.PLUGIN_DISABLED)
        return ReturnResult.COMMIT
      }

      val isAmend = panel.isAmend()
      val threshold = userSettings.threshold
      val (executionTime, predictedFiles) = measureSupplierTimeMillis {
        ProgressManager.getInstance()
          .runProcessWithProgressSynchronously(
            ThrowableComputable<List<PredictedFile>, Exception> {
              getPredictedFiles(rootFiles, isAmend, threshold)
            },
            "Calculating whether something should be added to this commit",
            true,
            project
          )
      }
      logEvent(project, ChangeReminderEvent.PREDICTION_CALCULATED, ChangeReminderData.EXECUTION_TIME, executionTime)

      if (predictedFiles.isEmpty()) {
        logEvent(project, ChangeReminderEvent.NOT_SHOWED)
        return ReturnResult.COMMIT
      }

      val dialog = ChangeReminderDialog(project, predictedFiles)
      val showDialogTime = measureTimeMillis {
        dialog.show()
      }
      logEvent(project, ChangeReminderEvent.DIALOG_CLOSED, ChangeReminderData.SHOW_DIALOG_TIME, showDialogTime)

      return if (dialog.exitCode == 1) {
        logEvent(project, ChangeReminderEvent.COMMIT_CANCELED)
        userSettings.updateState(UserSettings.Companion.UserAction.CANCEL)
        ReturnResult.CANCEL
      }
      else {
        logEvent(project, ChangeReminderEvent.COMMITTED_ANYWAY)
        userSettings.updateState(UserSettings.Companion.UserAction.COMMIT)
        ReturnResult.COMMIT
      }
    }
    catch (e: Exception) {
      LOG.error("Unexpected problem with ChangeReminder prediction", e)
      return ReturnResult.COMMIT
    }
  }

}