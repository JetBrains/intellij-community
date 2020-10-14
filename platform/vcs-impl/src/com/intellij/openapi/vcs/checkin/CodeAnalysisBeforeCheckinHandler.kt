// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkin

import com.intellij.CommonBundle
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
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.CodeSmellDetector
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.ui.BooleanCommitOption
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.ExceptionUtil
import com.intellij.util.PairConsumer
import com.intellij.util.ui.UIUtil

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
    BooleanCommitOption(myCheckinPanel, VcsBundle.message("before.checkin.standard.options.check.smells"), true,
                        settings::CHECK_CODE_SMELLS_BEFORE_PROJECT_COMMIT)

  private val settings: VcsConfiguration get() = VcsConfiguration.getInstance(myProject)

  private fun processFoundCodeSmells(codeSmells: List<CodeSmellInfo>, executor: CommitExecutor?): ReturnResult {
    val errorCount = collectErrors(codeSmells)
    val warningCount = codeSmells.size - errorCount
    val virtualFiles = codeSmells.mapTo(mutableSetOf()) { FileDocumentManager.getInstance().getFile(it.document) }
    var commitButtonText = executor?.actionText ?: myCheckinPanel.commitActionName
    commitButtonText = StringUtil.trimEnd(commitButtonText!!, "...")
    val message = if (virtualFiles.size == 1) VcsBundle.message("before.commit.file.contains.code.smells.edit.them.confirm.text",
                                                                FileUtil.toSystemDependentName(
                                                                  FileUtil.getLocationRelativeToUserHome(
                                                                    virtualFiles.first()!!.path)), errorCount, warningCount)
    else VcsBundle.message("before.commit.files.contain.code.smells.edit.them.confirm.text",
                           virtualFiles.size, errorCount, warningCount)
    val answer = Messages.showYesNoCancelDialog(myProject,
                                                message,
                                                VcsBundle.message("code.smells.error.messages.tab.name"),
                                                VcsBundle.message("code.smells.review.button"),
                                                commitButtonText, CommonBundle.getCancelButtonText(), UIUtil.getWarningIcon())
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
      return if (Messages.showOkCancelDialog(myProject, VcsBundle.message("code.smells.error.indexing.message",
                                                                          ApplicationNamesInfo.getInstance().productName),
                                             VcsBundle.message("code.smells.error.indexing"),
                                             VcsBundle.message("checkin.wait"), VcsBundle.message("checkin.commit"), null) == Messages.OK) {
        ReturnResult.CANCEL
      }
      else ReturnResult.COMMIT
    }

    return try {
      runCodeAnalysis(executor)
    }
    catch (e: ProcessCanceledException) {
      ReturnResult.CANCEL
    }
    catch (e: Exception) {
      LOG.error(e)
      if (Messages.showOkCancelDialog(myProject,
                                      VcsBundle.message("checkin.code.analysis.failed.with.exception.name.message", e.javaClass.name,
                                                        e.message),
                                      VcsBundle.message("checkin.code.analysis.failed"), VcsBundle.message("checkin.commit"),
                                      VcsBundle.message("checkin.cancel"), null) == Messages.OK) {
        ReturnResult.COMMIT
      }
      else ReturnResult.CANCEL
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
    ProgressManager.getInstance().run(object : Task.Modal(myProject, VcsBundle.message("checking.code.smells.progress.title"), true) {
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

  companion object {
    private fun collectErrors(codeSmells: List<CodeSmellInfo>): Int {
      var result = 0
      for (codeSmellInfo in codeSmells) {
        if (codeSmellInfo.severity === HighlightSeverity.ERROR) result++
      }
      return result
    }
  }
}