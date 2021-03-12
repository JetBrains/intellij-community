// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.checkin

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.actions.PerformFixesTask
import com.intellij.codeInspection.ex.CleanupProblems
import com.intellij.codeInspection.ex.GlobalInspectionContextBase
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl
import com.intellij.lang.LangBundle
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.CommandProcessorEx
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.ui.BooleanCommitOption
import com.intellij.openapi.vcs.checkin.CheckinHandlerUtil.filterOutGeneratedAndExcludedFiles
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.util.SequentialModalProgressTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CodeCleanupCheckinHandlerFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler = CodeCleanupCheckinHandler(panel)
}

private class CodeCleanupCheckinHandler(private val panel: CheckinProjectPanel) :
  CheckinHandler(),
  CheckinMetaHandler,
  CommitCheck<CommitProblem> {

  private val project = panel.project
  private val settings get() = VcsConfiguration.getInstance(project)

  override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent =
    BooleanCommitOption(panel, message("before.checkin.cleanup.code"), true, settings::CHECK_CODE_CLEANUP_BEFORE_PROJECT_COMMIT)

  override fun runCheckinHandlers(runnable: Runnable) {
    if (settings.CHECK_CODE_CLEANUP_BEFORE_PROJECT_COMMIT && !DumbService.isDumb(project)) {
      val filesToProcess = filterOutGeneratedAndExcludedFiles(panel.virtualFiles, project)
      val globalContext = InspectionManager.getInstance(project).createNewGlobalContext() as GlobalInspectionContextBase
      val profile = InspectionProjectProfileManager.getInstance(project).currentProfile

      globalContext.codeCleanup(AnalysisScope(project, filesToProcess), profile, null, runnable, true)
    }
    else {
      runnable.run()
    }
  }

  override fun isEnabled(): Boolean = settings.CHECK_CODE_CLEANUP_BEFORE_PROJECT_COMMIT

  override suspend fun runCheck(): CommitProblem? {
    val cleanupProblems = findProblems()
    applyFixes(cleanupProblems)

    return null
  }

  override fun showDetails(problem: CommitProblem) = Unit

  private suspend fun findProblems(): CleanupProblems {
    val files = filterOutGeneratedAndExcludedFiles(panel.virtualFiles, project)
    val globalContext = InspectionManager.getInstance(project).createNewGlobalContext() as GlobalInspectionContextImpl
    val profile = InspectionProjectProfileManager.getInstance(project).currentProfile
    val scope = AnalysisScope(project, files)
    val indicator = EmptyProgressIndicator()

    return withContext(Dispatchers.Default) {
      ProgressManager.getInstance().runProcess(
        Computable { globalContext.findProblems(scope, profile, indicator) { true } },
        indicator
      )
    }
  }

  private suspend fun applyFixes(cleanupProblems: CleanupProblems) {
    if (cleanupProblems.files.isEmpty()) return
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(cleanupProblems.files)) return

    val commandProcessor = CommandProcessor.getInstance() as CommandProcessorEx
    commandProcessor.executeCommand {
      if (cleanupProblems.isGlobalScope) commandProcessor.markCurrentCommandAsGlobal(project)

      val runner = SequentialModalProgressTask(project, "", true)
      runner.setMinIterationTime(200)
      runner.setTask(PerformFixesTask(project, cleanupProblems.problemDescriptors, null))

      withContext(Dispatchers.IO) {
        runner.doRun(EmptyProgressIndicator())
      }
    }
  }

  private suspend fun CommandProcessorEx.executeCommand(block: suspend () -> Unit) {
    val commandToken = startCommand(project, LangBundle.message("code.cleanup"), null, UndoConfirmationPolicy.DEFAULT)!!
    try {
      block()
    }
    finally {
      finishCommand(commandToken, null)
    }
  }
}