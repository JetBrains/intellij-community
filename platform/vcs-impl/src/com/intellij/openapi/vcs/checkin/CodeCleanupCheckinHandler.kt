// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.vcs.checkin

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInspection.CommonProblemDescriptor
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.InspectionProfile
import com.intellij.codeInspection.actions.PerformFixesTask
import com.intellij.codeInspection.ex.CleanupProblems
import com.intellij.codeInspection.ex.GlobalInspectionContextBase
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl
import com.intellij.lang.LangBundle
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.CommandProcessorEx
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressWrapper
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinHandlerUtil.filterOutGeneratedAndExcludedFiles
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.profile.codeInspection.InspectionProfileManager
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
    ProfileChooser(panel, settings::CHECK_CODE_CLEANUP_BEFORE_PROJECT_COMMIT,
                   settings::CHECK_CODE_CLEANUP_BEFORE_PROJECT_COMMIT_LOCAL,
                   settings::CHECK_CODE_CLEANUP_BEFORE_PROJECT_COMMIT_PROFILE,
                   "before.checkin.cleanup.code",
                   "before.checkin.cleanup.code.profile")

  /**
   * Won't be called for Commit Tool Window as [CommitCheck] is implemented.
   *
   * @see com.intellij.vcs.commit.NonModalCommitWorkflow.runMetaHandler
   */
  override fun runCheckinHandlers(runnable: Runnable) {
    if (settings.CHECK_CODE_CLEANUP_BEFORE_PROJECT_COMMIT && !DumbService.isDumb(project)) {
      val filesToProcess = filterOutGeneratedAndExcludedFiles(panel.virtualFiles, project)
      val globalContext = InspectionManager.getInstance(project).createNewGlobalContext() as GlobalInspectionContextBase
      val profile = getProfile()

      globalContext.codeCleanup(AnalysisScope(project, filesToProcess), profile, null, runnable, true)
    }
    else {
      runnable.run()
    }
  }

  override fun isEnabled(): Boolean = settings.CHECK_CODE_CLEANUP_BEFORE_PROJECT_COMMIT

  override suspend fun runCheck(indicator: ProgressIndicator): CommitProblem? {
    indicator.text = message("progress.text.inspecting.code")
    val cleanupProblems = findProblems(indicator)

    indicator.text = message("progress.text.applying.fixes")
    indicator.text2 = ""
    applyFixes(cleanupProblems, indicator)

    return null
  }

  /**
   * Does nothing as no problem is reported in [runCheck].
   */
  override fun showDetails(problem: CommitProblem) = Unit

  private suspend fun findProblems(indicator: ProgressIndicator): CleanupProblems {
    val files = filterOutGeneratedAndExcludedFiles(panel.virtualFiles, project)
    val globalContext = InspectionManager.getInstance(project).createNewGlobalContext() as GlobalInspectionContextImpl
    val profile = getProfile()
    val scope = AnalysisScope(project, files)
    val wrapper = TextToText2Indicator(ProgressWrapper.wrap(indicator))

    return withContext(Dispatchers.Default) {
      ProgressManager.getInstance().runProcess(
        Computable { globalContext.findProblems(scope, profile, wrapper) { true } },
        wrapper
      )
    }
  }

  private fun getProfile(): InspectionProfile {
    val cleanupProfile = settings.CHECK_CODE_CLEANUP_BEFORE_PROJECT_COMMIT_PROFILE
    if (cleanupProfile != null) {
      val profileManager = if (settings.CHECK_CODE_CLEANUP_BEFORE_PROJECT_COMMIT_LOCAL) InspectionProfileManager.getInstance()
      else InspectionProjectProfileManager.getInstance(project)
      
      return profileManager.getProfile(cleanupProfile)
    }
    return InspectionProjectProfileManager.getInstance(project).currentProfile
  }

  private suspend fun applyFixes(cleanupProblems: CleanupProblems, indicator: ProgressIndicator) {
    if (cleanupProblems.files.isEmpty()) return
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(cleanupProblems.files)) return

    val commandProcessor = CommandProcessor.getInstance() as CommandProcessorEx
    commandProcessor.executeCommand {
      if (cleanupProblems.isGlobalScope) commandProcessor.markCurrentCommandAsGlobal(project)

      val runner = SequentialModalProgressTask(project, "", true)
      runner.setMinIterationTime(200)
      runner.setTask(ApplyFixesTask(project, cleanupProblems.problemDescriptors, indicator))

      withContext(Dispatchers.IO) {
        runner.doRun(NoTextIndicator(indicator))
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

private class ApplyFixesTask(project: Project, descriptors: List<CommonProblemDescriptor>, private val indicator: ProgressIndicator) :
  PerformFixesTask(project, descriptors, null) {

  override fun beforeProcessing(descriptor: CommonProblemDescriptor) {
    indicator.text2 = getPresentableText(descriptor)
  }
}