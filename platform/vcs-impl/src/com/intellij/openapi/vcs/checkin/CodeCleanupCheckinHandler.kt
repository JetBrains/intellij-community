// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.vcs.checkin

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInspection.CommonProblemDescriptor
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.InspectionProfile
import com.intellij.codeInspection.actions.PerformFixesTask
import com.intellij.codeInspection.ex.CleanupProblems
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl
import com.intellij.lang.LangBundle
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.CommandProcessorEx
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.ProgressSink
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.progress.progressSink
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinHandlerUtil.filterOutGeneratedAndExcludedFiles
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.util.SequentialModalProgressTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class CodeCleanupCheckinHandlerFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
    return CodeCleanupCheckinHandler(panel.project)
  }
}

private class CodeCleanupCheckinHandler(private val project: Project) :
  CheckinHandler(),
  CommitCheck {

  private val settings get() = VcsConfiguration.getInstance(project)

  override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent =
    ProfileChooser(project,
                   settings::CHECK_CODE_CLEANUP_BEFORE_PROJECT_COMMIT,
                   settings::CHECK_CODE_CLEANUP_BEFORE_PROJECT_COMMIT_LOCAL,
                   settings::CHECK_CODE_CLEANUP_BEFORE_PROJECT_COMMIT_PROFILE,
                   "before.checkin.cleanup.code",
                   "before.checkin.cleanup.code.profile")
      .build(this)

  override fun getExecutionOrder(): CommitCheck.ExecutionOrder = CommitCheck.ExecutionOrder.MODIFICATION

  override fun isEnabled(): Boolean = settings.CHECK_CODE_CLEANUP_BEFORE_PROJECT_COMMIT

  override suspend fun runCheck(commitInfo: CommitInfo): CommitProblem? {
    val sink = coroutineContext.progressSink
    sink?.text(message("progress.text.inspecting.code"))
    val cleanupProblems = findProblems(commitInfo.committedVirtualFiles)

    sink?.text(message("progress.text.applying.fixes"))
    sink?.details("")
    applyFixes(cleanupProblems)

    return null
  }

  private suspend fun findProblems(committedFiles: List<VirtualFile>): CleanupProblems {
    val globalContext = InspectionManager.getInstance(project).createNewGlobalContext() as GlobalInspectionContextImpl
    val profile = getProfile()
    return withContext(Dispatchers.Default + textToDetailsSinkContext(coroutineContext.progressSink)) {
      val files = readAction { filterOutGeneratedAndExcludedFiles(committedFiles, project) }
      val scope = AnalysisScope(project, files)
      coroutineToIndicator {
        val indicator = ProgressManager.getGlobalProgressIndicator()
        globalContext.findProblems(scope, profile, indicator) { true }
      }
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

  private suspend fun applyFixes(cleanupProblems: CleanupProblems) {
    if (cleanupProblems.files().isEmpty()) return
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(cleanupProblems.files())) return

    val commandProcessor = CommandProcessor.getInstance() as CommandProcessorEx
    commandProcessor.executeCommand {
      if (cleanupProblems.isGlobalScope()) commandProcessor.markCurrentCommandAsGlobal(project)

      val sink = coroutineContext.progressSink
      val runner = SequentialModalProgressTask(project, "", true)
      runner.setMinIterationTime(200)
      runner.setTask(ApplyFixesTask(project, cleanupProblems.problemDescriptors(), sink))

      withContext(Dispatchers.IO + noTextSinkContext(sink)) {
        coroutineToIndicator {
          // TODO get rid of SequentialModalProgressTask
          runner.doRun(ProgressManager.getGlobalProgressIndicator())
        }
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

private class ApplyFixesTask(project: Project, descriptors: List<CommonProblemDescriptor>, private val sink: ProgressSink?) :
  PerformFixesTask(project, descriptors, null) {

  override fun beforeProcessing(descriptor: CommonProblemDescriptor) {
    sink?.update(details = getPresentableText(descriptor))
  }
}