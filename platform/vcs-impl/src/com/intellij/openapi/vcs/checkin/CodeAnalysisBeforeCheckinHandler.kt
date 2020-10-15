// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkin

import com.intellij.CommonBundle.getCancelButtonText
import com.intellij.codeInsight.CodeSmellInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.yesNo
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.yesNoCancel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.YesNoCancelResult
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil.getLocationRelativeToUserHome
import com.intellij.openapi.util.io.FileUtil.toSystemDependentName
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.CodeSmellDetector
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.ui.BooleanCommitOption
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.ExceptionUtil
import com.intellij.util.PairConsumer
import com.intellij.util.ui.UIUtil.getWarningIcon

private val LOG = logger<CodeAnalysisBeforeCheckinHandler>()

class CodeAnalysisCheckinHandlerFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler =
    CodeAnalysisBeforeCheckinHandler(panel.project, panel)
}

/**
 * The check-in handler which performs code analysis before check-in. Source code for this class
 * is provided as a sample of using the [CheckinHandler] API.
 *
 * @author lesya
 */
class CodeAnalysisBeforeCheckinHandler(private val myProject: Project,
                                       private val myCheckinPanel: CheckinProjectPanel) : CheckinHandler(), CommitCheck {
  override fun isEnabled(): Boolean = settings.CHECK_CODE_SMELLS_BEFORE_PROJECT_COMMIT

  override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent =
    BooleanCommitOption(myCheckinPanel, message("before.checkin.standard.options.check.smells"), true,
                        settings::CHECK_CODE_SMELLS_BEFORE_PROJECT_COMMIT)

  private val settings: VcsConfiguration get() = VcsConfiguration.getInstance(myProject)

  private fun processFoundCodeSmells(codeSmells: List<CodeSmellInfo>, executor: CommitExecutor?): ReturnResult {
    var commitButtonText = executor?.actionText ?: myCheckinPanel.commitActionName
    commitButtonText = StringUtil.trimEnd(commitButtonText!!, "...")

    val answer = askReviewCommitCancel(myProject, codeSmells, commitButtonText)
    if (answer == Messages.YES) {
      CodeSmellDetector.getInstance(myProject).showCodeSmellErrors(codeSmells)
      return ReturnResult.CLOSE_WINDOW
    }
    return if (answer == Messages.CANCEL) {
      ReturnResult.CANCEL
    }
    else ReturnResult.COMMIT
  }

  override fun beforeCheckin(executor: CommitExecutor?, additionalDataConsumer: PairConsumer<Any, Any>): ReturnResult {
    if (!isEnabled()) return ReturnResult.COMMIT
    if (DumbService.getInstance(myProject).isDumb) {
      return if (confirmCommitInDumbMode(myProject)) ReturnResult.COMMIT else ReturnResult.CANCEL
    }

    return try {
      runCodeAnalysis(executor)
    }
    catch (e: ProcessCanceledException) {
      ReturnResult.CANCEL
    }
    catch (e: Exception) {
      LOG.error(e)
      if (confirmCommitWithCodeAnalysisFailure(myProject, e)) ReturnResult.COMMIT else ReturnResult.CANCEL
    }
  }

  private fun runCodeAnalysis(commitExecutor: CommitExecutor?): ReturnResult {
    val files = CheckinHandlerUtil.filterOutGeneratedAndExcludedFiles(myCheckinPanel.virtualFiles, myProject)
    return if (files.size <= Registry.intValue("vcs.code.analysis.before.checkin.show.only.new.threshold", 0)) {
      runCodeAnalysisNew(commitExecutor, files)
    }
    else runCodeAnalysisOld(commitExecutor, files)
  }

  private fun runCodeAnalysisNew(commitExecutor: CommitExecutor?, files: List<VirtualFile>): ReturnResult {
    val codeSmells = Ref.create<List<CodeSmellInfo>>()
    val exception = Ref.create<Exception>()
    PsiDocumentManager.getInstance(myProject).commitAllDocuments()
    ProgressManager.getInstance().run(object : Task.Modal(myProject, message("checking.code.smells.progress.title"), true) {
      override fun run(indicator: ProgressIndicator) {
        try {
          assert(myProject != null)
          indicator.isIndeterminate = true
          codeSmells.set(CodeAnalysisBeforeCheckinShowOnlyNew.runAnalysis(myProject, files, indicator))
          indicator.text = VcsBundle.getString("before.checkin.waiting.for.smart.mode")
          DumbService.getInstance(myProject).waitForSmartMode()
        }
        catch (e: ProcessCanceledException) {
          LOG.info("Code analysis canceled", e)
          exception.set(e)
        }
        catch (e: Exception) {
          LOG.error(e)
          exception.set(e)
        }
      }
    })
    if (!exception.isNull) {
      ExceptionUtil.rethrowAllAsUnchecked(exception.get())
    }
    return if (codeSmells.get().isNotEmpty()) processFoundCodeSmells(codeSmells.get(), commitExecutor) else ReturnResult.COMMIT
  }

  private fun runCodeAnalysisOld(commitExecutor: CommitExecutor?, files: List<VirtualFile>): ReturnResult {
    val codeSmells = CodeSmellDetector.getInstance(myProject).findCodeSmells(files)
    return if (codeSmells.isNotEmpty()) processFoundCodeSmells(codeSmells, commitExecutor) else ReturnResult.COMMIT
  }
}

private fun confirmCommitInDumbMode(project: Project) =
  !yesNo(message("code.smells.error.indexing"),
         message("code.smells.error.indexing.message", ApplicationNamesInfo.getInstance().productName))
    .icon(null)
    .yesText(message("checkin.wait"))
    .noText(message("checkin.commit"))
    .ask(project)

private fun confirmCommitWithCodeAnalysisFailure(project: Project, e: Exception) =
  yesNo(message("checkin.code.analysis.failed"),
        message("checkin.code.analysis.failed.with.exception.name.message", e.javaClass.name, e.message))
    .icon(null)
    .yesText(message("checkin.commit"))
    .noText(message("checkin.cancel"))
    .ask(project)

@YesNoCancelResult
private fun askReviewCommitCancel(project: Project, codeSmells: List<CodeSmellInfo>, @NlsContexts.Button commitActionText: String): Int =
  yesNoCancel(message("code.smells.error.messages.tab.name"), getDescription(codeSmells))
    .icon(getWarningIcon())
    .yesText(message("code.smells.review.button"))
    .noText(commitActionText)
    .cancelText(getCancelButtonText())
    .show(project)

@DialogMessage
private fun getDescription(codeSmells: List<CodeSmellInfo>): String {
  val errorCount = codeSmells.count { it.severity == HighlightSeverity.ERROR }
  val warningCount = codeSmells.size - errorCount
  val virtualFiles = codeSmells.mapTo(mutableSetOf()) { FileDocumentManager.getInstance().getFile(it.document) }

  if (virtualFiles.size == 1) {
    val path = toSystemDependentName(getLocationRelativeToUserHome(virtualFiles.first()!!.path))
    return message("before.commit.file.contains.code.smells.edit.them.confirm.text", path, errorCount, warningCount)
  }

  return message("before.commit.files.contain.code.smells.edit.them.confirm.text", virtualFiles.size, errorCount, warningCount)
}