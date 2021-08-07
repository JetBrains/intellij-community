// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkin

import com.intellij.CommonBundle.getCancelButtonText
import com.intellij.codeInsight.CodeSmellInfo
import com.intellij.codeInsight.actions.VcsFacadeImpl
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionProfileWrapper
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.nls.NlsMessages.formatAndList
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbService.isDumb
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.yesNo
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.yesNoCancel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.YesNoCancelResult
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
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.util.ExceptionUtil.rethrowUnchecked
import com.intellij.util.PairConsumer
import com.intellij.util.containers.ConcurrentFactoryMap
import com.intellij.util.progress.DelegatingProgressIndicatorEx
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil.getWarningIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.PropertyKey
import javax.swing.JComponent
import kotlin.reflect.KMutableProperty0

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

  override suspend fun runCheck(indicator: ProgressIndicator): CodeAnalysisCommitProblem? {
    indicator.text = message("progress.text.analyzing.code")

    val files = filterOutGeneratedAndExcludedFiles(commitPanel.virtualFiles, project)
    PsiDocumentManager.getInstance(project).commitAllDocuments()

    lateinit var codeSmells: List<CodeSmellInfo>
    withContext(Dispatchers.Default) {
      ProgressManager.getInstance().executeProcessUnderProgress(
        { codeSmells = CodeSmellDetector.getInstance(project).findCodeSmells(files) },
        TextToText2IndicatorEx(indicator as ProgressIndicatorEx) // [findCodeSmells] requires [ProgressIndicatorEx] set for thread
      )
    }
    return if (codeSmells.isNotEmpty()) CodeAnalysisCommitProblem(codeSmells) else null
  }

  override fun showDetails(problem: CodeAnalysisCommitProblem) =
    CodeSmellDetector.getInstance(project).showCodeSmellErrors(problem.codeSmells)

  override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent =
    ProfileChooser(commitPanel, 
                   settings::CHECK_CODE_SMELLS_BEFORE_PROJECT_COMMIT, 
                   settings::CODE_SMELLS_PROFILE_LOCAL, 
                   settings::CODE_SMELLS_PROFILE, 
                   "before.checkin.standard.options.check.smells",
                   "before.checkin.options.check.smells.profile")

  override fun beforeCheckin(executor: CommitExecutor?, additionalDataConsumer: PairConsumer<Any, Any>): ReturnResult {
    if (!isEnabled()) return ReturnResult.COMMIT
    if (isDumb(project)) return if (confirmCommitInDumbMode(project)) ReturnResult.COMMIT else ReturnResult.CANCEL
    val psiFiles = processPsiFiles(commitPanel.virtualFiles)
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
    finally {
      for (it in psiFiles) {
        it.putUserData(InspectionProfileWrapper.PSI_ELEMENTS_BEING_COMMITTED, null)
      }
    }
  }

  /**
   * Extracts PsiFile elements from the VirtualFile elements in commitPanel and puts a closure in their user data
   * that extracts PsiElement elements that are being committed.
   * The closure accepts a class instance and returns a set of PsiElement elements that are changed or added.
   * The PsiFile elements are returned as a result.
   */
  private fun processPsiFiles(virtualFiles: Collection<VirtualFile>): List<PsiFile> {
    val files = filterOutGeneratedAndExcludedFiles(virtualFiles, project)

    val analyzeOnlyChangedProperties = Registry.`is`("vcs.code.analysis.before.checkin.check.unused.only.changed.properties", false)
    val psiFiles =
      if (!analyzeOnlyChangedProperties) emptyList()
      else runReadAction { files.mapNotNull { PsiManager.getInstance(project).findFile(it) } }

    for (file in psiFiles) {
      file.putUserData(InspectionProfileWrapper.PSI_ELEMENTS_BEING_COMMITTED,
                       ConcurrentFactoryMap.createMap { getBeingCommittedPsiElements(it) })
    }
    return psiFiles
  }

  /**
   * Returns a set of PsiElements that are being committed
   */
  private fun getBeingCommittedPsiElements(clazz: Class<out PsiElement>): Set<PsiElement> {
    val vcs = VcsFacadeImpl.getVcsInstance()
    val changes = commitPanel.selectedChanges.toTypedArray()
    val elementsExtractor = { virtualFile : VirtualFile ->
      val psiFile = runReadAction {
        PsiManager.getInstance(project).findFile(virtualFile)
      }
      PsiTreeUtil.findChildrenOfType(psiFile, clazz).toList()
    }
    val beingCommittedPsiElements = vcs.getChangedElements(project, changes, elementsExtractor)
    return beingCommittedPsiElements.toSet()
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

class ProfileChooser(commitPanel: CheckinProjectPanel,
                     property: KMutableProperty0<Boolean>,
                     private val isLocalProperty : KMutableProperty0<Boolean>,
                     private val profileProperty : KMutableProperty0<String?>,
                     private val emptyTitleKey : @PropertyKey(resourceBundle = "messages.VcsBundle") String,
                     private val profileTitleKey : @PropertyKey(resourceBundle = "messages.VcsBundle") String) 
  : BooleanCommitOption(commitPanel, message(emptyTitleKey), true, property) {

  private val project = commitPanel.project

  override fun getComponent(): JComponent {
    var profile: InspectionProfileImpl? = null
    val profileName = profileProperty.get()
    if (profileName != null) {
      val manager = if (isLocalProperty.get()) InspectionProfileManager.getInstance()
      else InspectionProjectProfileManager.getInstance(project)
      profile = manager.getProfile(profileName)
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
      message(emptyTitleKey)
    else message(profileTitleKey, profile.displayName)
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
      group.add(object : AnAction() {
        init {
          templatePresentation.setText(profile.displayName, false)
          templatePresentation.icon = if (profileProperty.get() == profile.name) AllIcons.Actions.Checked else null
        }

        override fun actionPerformed(e: AnActionEvent) {
          profileProperty.set(profile.name)
          isLocalProperty.set(manager !is InspectionProjectProfileManager)
          setProfileText(profile)
        }
      })
    }
  }
}

private class TextToText2IndicatorEx(indicator: ProgressIndicatorEx) : DelegatingProgressIndicatorEx(indicator) {
  override fun setText(text: String?) = super.setText2(text)
  override fun setText2(text: String?) = Unit
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
    val codeSmells = CodeAnalysisBeforeCheckinShowOnlyNew.runAnalysis(project, files, indicator)

    indicator.text = message("before.checkin.waiting.for.smart.mode")
    DumbService.getInstance(project).waitForSmartMode()

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