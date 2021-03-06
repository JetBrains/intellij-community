// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkin

import com.intellij.CommonBundle.getCancelButtonText
import com.intellij.codeInsight.CodeSmellInfo
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.ide.IdeBundle
import com.intellij.ide.nls.NlsMessages.formatAndList
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbService.isDumb
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.yesNo
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.yesNoCancel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.YesNoCancelResult
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.openapi.util.io.FileUtil.getLocationRelativeToUserHome
import com.intellij.openapi.util.io.FileUtil.toSystemDependentName
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil.removeEllipsisSuffix
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.CodeSmellDetector
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.ui.BooleanCommitOption
import com.intellij.openapi.vcs.checkin.CheckinHandlerUtil.filterOutGeneratedAndExcludedFiles
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.util.ExceptionUtil.rethrowUnchecked
import com.intellij.util.PairConsumer
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil.getWarningIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.swing.JComponent

private val LOG = logger<CodeAnalysisBeforeCheckinHandler>()

class CodeAnalysisCheckinHandlerFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler =
    CodeAnalysisBeforeCheckinHandler(panel)
}

class CodeAnalysisCommitProblem(val codeSmells: List<CodeSmellInfo>) : CommitProblem {
  override val text: String
    get() {
      val errors = codeSmells.count { it.severity == HighlightSeverity.ERROR }
      val warnings = codeSmells.size - errors

      val errorsText = if (errors > 0) HighlightSeverity.ERROR.getCountMessage(errors) else null
      val warningsText = if (warnings > 0) HighlightSeverity.WARNING.getCountMessage(warnings) else null

      return formatAndList(listOfNotNull(errorsText, warningsText))
    }
}

/**
 * The check-in handler which performs code analysis before check-in. Source code for this class
 * is provided as a sample of using the [CheckinHandler] API.
 */
class CodeAnalysisBeforeCheckinHandler(private val commitPanel: CheckinProjectPanel) :
  CheckinHandler(), CommitCheck<CodeAnalysisCommitProblem> {

  private val project: Project get() = commitPanel.project
  private val settings: VcsConfiguration get() = VcsConfiguration.getInstance(project)

  override fun isEnabled(): Boolean = settings.CHECK_CODE_SMELLS_BEFORE_PROJECT_COMMIT

  override suspend fun runCheck(): CodeAnalysisCommitProblem? {
    val files = filterOutGeneratedAndExcludedFiles(commitPanel.virtualFiles, project)
    PsiDocumentManager.getInstance(project).commitAllDocuments()

    val codeSmells = withContext(Dispatchers.Default) {
      ProgressManager.getInstance().runProcess(
        Computable { CodeSmellDetector.getInstance(project).findCodeSmells(files) },
        ProgressIndicatorBase().apply { isIndeterminate = false } // [findCodeSmells] requires [ProgressIndicatorEx] set for thread
      )
    }
    return if (codeSmells.isNotEmpty()) CodeAnalysisCommitProblem(codeSmells) else null
  }

  override fun showDetails(problem: CodeAnalysisCommitProblem) =
    CodeSmellDetector.getInstance(project).showCodeSmellErrors(problem.codeSmells)

  override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent =
    object : BooleanCommitOption(commitPanel, message("before.checkin.standard.options.check.smells"), true,
                                 settings::CHECK_CODE_SMELLS_BEFORE_PROJECT_COMMIT) {
      override fun getComponent(): JComponent {
        var profile: InspectionProfileImpl? = null
        if (settings.CODE_SMELLS_PROFILE != null) {
          val manager = if (settings.CODE_SMELLS_PROFILE_LOCAL) InspectionProfileManager.getInstance() else InspectionProjectProfileManager.getInstance(project)
          profile = manager.getProfile(settings.CODE_SMELLS_PROFILE)
        }
        setProfileText(profile)

        val showFiltersPopup = LinkListener<Any> { sourceLink, _ ->
          JBPopupMenu.showBelow(sourceLink, ActionPlaces.CODE_INSPECTION, createProfileChooser())
        }
        val configureFilterLink = LinkLabel(message("before.checkin.options.check.smells.choose.profile"), null, showFiltersPopup)

        return JBUI.Panels.simplePanel(4, 0).addToLeft(checkBox).addToCenter(configureFilterLink)
      }

      private fun setProfileText(profile: InspectionProfileImpl?) {
        checkBox.text = if (profile == null || profile == InspectionProjectProfileManager.getInstance(project).currentProfile)
          message("before.checkin.standard.options.check.smells")
          else message("before.checkin.options.check.smells.profile", profile.displayName)
      }

      private fun createProfileChooser(): DefaultActionGroup {
        val group = DefaultActionGroup()
        group.add(Separator.create(IdeBundle.message("separator.scheme.stored.in", IdeBundle.message("scheme.project"))))
        fillActions(group, InspectionProjectProfileManager.getInstance(project))
        group.add(Separator.create(IdeBundle.message("separator.scheme.stored.in", IdeBundle.message("scheme.ide"))))
        fillActions(group, InspectionProfileManager.getInstance())
        return group
      }

      private fun fillActions(group: DefaultActionGroup, manager: InspectionProfileManager) {
        for (profile in manager.profiles) {
          group.add(object : AnAction(profile.displayName) {
            override fun actionPerformed(e: AnActionEvent) {
              settings.CODE_SMELLS_PROFILE = profile.name
              settings.CODE_SMELLS_PROFILE_LOCAL = manager !is InspectionProjectProfileManager
              setProfileText(profile)
            }
          })
        }
      }
    }

  override fun beforeCheckin(executor: CommitExecutor?, additionalDataConsumer: PairConsumer<Any, Any>): ReturnResult {
    if (!isEnabled()) return ReturnResult.COMMIT
    if (isDumb(project)) return if (confirmCommitInDumbMode(project)) ReturnResult.COMMIT else ReturnResult.CANCEL

    return try {
      val codeSmells = findCodeSmells()
      if (codeSmells.isEmpty()) ReturnResult.COMMIT else processFoundCodeSmells(codeSmells, executor)
    }
    catch (e: ProcessCanceledException) {
      ReturnResult.CANCEL
    }
    catch (e: Exception) {
      LOG.error(e)
      if (confirmCommitWithCodeAnalysisFailure(project, e)) ReturnResult.COMMIT else ReturnResult.CANCEL
    }
  }

  private fun findCodeSmells(): List<CodeSmellInfo> {
    val files = filterOutGeneratedAndExcludedFiles(commitPanel.virtualFiles, project)
    val newAnalysisThreshold = Registry.intValue("vcs.code.analysis.before.checkin.show.only.new.threshold", 0)

    if (files.size > newAnalysisThreshold) return CodeSmellDetector.getInstance(project).findCodeSmells(files)

    PsiDocumentManager.getInstance(project).commitAllDocuments()
    return FindNewCodeSmellsTask(project, files).find()
  }

  private fun processFoundCodeSmells(codeSmells: List<CodeSmellInfo>, executor: CommitExecutor?): ReturnResult {
    val commitActionText = removeEllipsisSuffix(executor?.actionText ?: commitPanel.commitActionName)

    return when (askReviewCommitCancel(project, codeSmells, commitActionText)) {
      Messages.YES -> {
        CodeSmellDetector.getInstance(project).showCodeSmellErrors(codeSmells)
        ReturnResult.CLOSE_WINDOW
      }
      Messages.NO -> ReturnResult.COMMIT
      else -> ReturnResult.CANCEL
    }
  }
}

private class FindNewCodeSmellsTask(project: Project, private val files: List<VirtualFile>) :
  Task.WithResult<List<CodeSmellInfo>, Exception>(project, message("checking.code.smells.progress.title"), true) {

  fun find(): List<CodeSmellInfo> {
    queue()

    return try {
      result
    }
    catch (e: ProcessCanceledException) {
      LOG.info("Code analysis canceled", e)
      throw e
    }
    catch (e: Exception) {
      LOG.error(e)
      rethrowUnchecked(e)
      throw RuntimeException(e)
    }
  }

  override fun compute(indicator: ProgressIndicator): List<CodeSmellInfo> {
    indicator.isIndeterminate = true
    val codeSmells = CodeAnalysisBeforeCheckinShowOnlyNew.runAnalysis(myProject!!, files, indicator)

    indicator.text = message("before.checkin.waiting.for.smart.mode")
    DumbService.getInstance(myProject).waitForSmartMode()

    return codeSmells
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