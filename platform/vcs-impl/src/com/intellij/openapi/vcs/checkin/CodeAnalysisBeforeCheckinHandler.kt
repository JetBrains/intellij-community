// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.application.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.StandardProgressIndicator
import com.intellij.openapi.progress.jobToIndicator
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.yesNoCancel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.YesNoCancelResult
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.openapi.util.io.FileUtil.getLocationRelativeToUserHome
import com.intellij.openapi.util.io.FileUtil.toSystemDependentName
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.CodeSmellDetector
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.ui.BooleanCommitOption
import com.intellij.openapi.vcs.checkin.CheckinHandlerUtil.isGeneratedOrExcluded
import com.intellij.openapi.vcs.checkin.CodeAnalysisBeforeCheckinHandler.Companion.processFoundCodeSmells
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.progress.RawProgressReporter
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.platform.util.progress.withProgressText
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.containers.ConcurrentFactoryMap
import com.intellij.util.ui.UIUtil.getWarningIcon
import com.intellij.vcs.commit.CommitSessionCollector
import com.intellij.vcs.commit.isPostCommitCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import kotlin.reflect.KMutableProperty0

class CodeAnalysisCheckinHandlerFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler =
    CodeAnalysisBeforeCheckinHandler(panel.project)
}

class CodeAnalysisCommitProblem(private val codeSmells: List<CodeSmellInfo>,
                                private val errors: Int, private val warnings: Int) : CommitProblemWithDetails {
  override val text: String
    get() {
      val errorsText = if (errors > 0) HighlightSeverity.ERROR.getCountMessage(errors) else null
      val warningsText = if (warnings > 0) HighlightSeverity.WARNING.getCountMessage(warnings) else null

      return formatAndList(listOfNotNull(errorsText, warningsText))
    }

  override fun showDetails(project: Project) {
    CodeSmellDetector.getInstance(project).showCodeSmellErrors(codeSmells)
  }

  override fun showModalSolution(project: Project, commitInfo: CommitInfo): CheckinHandler.ReturnResult {
    return processFoundCodeSmells(project, codeSmells, commitInfo.commitActionText)
  }

  override val showDetailsAction: String
    get() = message("code.smells.review.button")
}

/**
 * The check-in handler which performs code analysis before check-in. Source code for this class
 * is provided as a sample of using the [CheckinHandler] API.
 */
class CodeAnalysisBeforeCheckinHandler(private val project: Project) :
  CheckinHandler(), CommitCheck {

  private val settings: VcsConfiguration get() = VcsConfiguration.getInstance(project)

  override fun getExecutionOrder(): CommitCheck.ExecutionOrder = CommitCheck.ExecutionOrder.POST_COMMIT

  override fun isEnabled(): Boolean = settings.CHECK_CODE_SMELLS_BEFORE_PROJECT_COMMIT

  override suspend fun runCheck(commitInfo: CommitInfo): CodeAnalysisCommitProblem? {
    val isPostCommit = commitInfo.isPostCommitCheck
    val changes = commitInfo.committedChanges
    if (changes.isEmpty()) return null

    //readaction is not enough
    writeIntentReadAction {
      PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    val codeSmells: List<CodeSmellInfo> = withProgressText(message("progress.text.analyzing.code")) {
      withContext(Dispatchers.Default) {
        val changesByFile = groupChangesByFile(changes)
        reportRawProgress { reporter ->
          // [findCodeSmells] requires [ProgressIndicatorEx] set for thread
          val progressIndicatorEx = ProgressSinkIndicatorEx(
            reporter,
            coroutineContext.contextModality() ?: ModalityState.nonModal()
          )
          jobToIndicator(coroutineContext.job, progressIndicatorEx) {
            // TODO suspending [findCodeSmells]
            findCodeSmells(changesByFile, isPostCommit)
          }
        }
      }
    }
    if (codeSmells.isEmpty()) {
      return null
    }

    val errors = codeSmells.count { it.severity == HighlightSeverity.ERROR }
    val warnings = codeSmells.size - errors
    CommitSessionCollector.getInstance(project).logCodeAnalysisWarnings(warnings, errors)

    return CodeAnalysisCommitProblem(codeSmells, errors, warnings)
  }

  override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent =
    ProfileChooser(project,
                   settings::CHECK_CODE_SMELLS_BEFORE_PROJECT_COMMIT,
                   settings::CODE_SMELLS_PROFILE_LOCAL,
                   settings::CODE_SMELLS_PROFILE,
                   "before.checkin.standard.options.check.smells",
                   "before.checkin.options.check.smells.profile").build(this)

  private suspend fun groupChangesByFile(changes: List<Change>): Map<VirtualFile, Change> {
    val changesByFile = mutableMapOf<VirtualFile, Change>()
    for (change in changes) {
      val changeFile = readAction {
        change.afterRevision?.file?.virtualFile
          ?.takeUnless { isGeneratedOrExcluded(project, it) }
      }
      if (changeFile == null) continue

      val oldChange = changesByFile.put(changeFile, change)
      if (oldChange != null) {
        logger<CodeAnalysisCheckinHandlerFactory>().warn("Multiple changes for the same file: $oldChange, $change")
      }
    }
    return changesByFile
  }

  /**
   * Puts a closure in PsiFile user data that extracts PsiElement elements that are being committed.
   * The closure accepts a class instance and returns a set of PsiElement elements that are changed or added.
   */
  private fun withCommittedElementsContext(changedFiles: Map<VirtualFile, Change>, isPostCommit: Boolean): AccessToken {
    val analyzeOnlyChangedProperties = Registry.`is`("vcs.code.analysis.before.checkin.check.unused.only.changed.properties", false)
    if (!analyzeOnlyChangedProperties) return AccessToken.EMPTY_ACCESS_TOKEN

    val psiFiles = mutableListOf<PsiFile>()
    for ((file, change) in changedFiles) {
      val psiFile = runReadAction { if (file.isValid) PsiManager.getInstance(project).findFile(file) else null } ?: continue
      psiFile.putUserData(InspectionProfileWrapper.PSI_ELEMENTS_BEING_COMMITTED,
                          ConcurrentFactoryMap.createMap { clazz -> getBeingCommittedPsiElements(change, clazz, isPostCommit) })
      psiFiles += psiFile
    }
    return object : AccessToken() {
      override fun finish() {
        for (it in psiFiles) {
          it.putUserData(InspectionProfileWrapper.PSI_ELEMENTS_BEING_COMMITTED, null)
        }
      }
    }
  }

  /**
   * Returns a set of PsiElements that are being committed
   */
  private fun getBeingCommittedPsiElements(change: Change, clazz: Class<out PsiElement>, isPostCommit: Boolean): Set<PsiElement> {
    val elementExtractor = { virtualFile: VirtualFile ->
      val psiFile = runReadAction {
        PsiManager.getInstance(project).findFile(virtualFile)
      }
      PsiTreeUtil.findChildrenOfType(psiFile, clazz).toList()
    }
    if (isPostCommit) {
      return VcsFacadeImpl.getVcsInstance().getPostCommitChangedElements(project, change, elementExtractor).toSet()
    }
    else {
      return VcsFacadeImpl.getVcsInstance().getLocalChangedElements(project, change, elementExtractor).toSet()
    }
  }

  private fun findCodeSmells(changedFiles: Map<VirtualFile, Change>, isPostCommit: Boolean): List<CodeSmellInfo> {
    try {
      withCommittedElementsContext(changedFiles, isPostCommit).use {
        val indicator = ProgressManager.getGlobalProgressIndicator()
        val newAnalysisThreshold = Registry.intValue("vcs.code.analysis.before.checkin.show.only.new.threshold", 0)
        val files = changedFiles.keys.toList()

        if (files.size > newAnalysisThreshold) {
          return CodeSmellDetector.getInstance(project).findCodeSmells(files)
        }
        else {
          indicator.isIndeterminate = true
          val codeSmells = CodeAnalysisBeforeCheckinShowOnlyNew.runAnalysis(project, files, indicator)

          // CodeAnalysisBeforeCheckinShowOnlyNew shelve-unshelve logic might start the dumb mode.
          // Wait for it to end, not to disturb the following pre-commit handlers.
          indicator.text = message("before.checkin.waiting.for.smart.mode")
          DumbService.getInstance(project).waitForSmartMode()

          return codeSmells
        }
      }
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Throwable) {
      logger<CodeAnalysisBeforeCheckinHandler>().error(e)
      return emptyList()
    }
  }

  companion object {
    internal fun processFoundCodeSmells(project: Project, codeSmells: List<CodeSmellInfo>, commitActionText: @Nls String): ReturnResult {
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
}

class ProfileChooser(private val project: Project,
                     private val property: KMutableProperty0<Boolean>,
                     private val isLocalProperty: KMutableProperty0<Boolean>,
                     private val profileProperty: KMutableProperty0<String?>,
                     private val emptyTitleKey: @PropertyKey(resourceBundle = "messages.VcsBundle") String,
                     private val profileTitleKey: @PropertyKey(resourceBundle = "messages.VcsBundle") String) {
  fun build(checkinHandler: CheckinHandler?): RefreshableOnComponent {
    var profile: InspectionProfileImpl? = null
    val profileName = profileProperty.get()
    if (profileName != null) {
      val manager = if (isLocalProperty.get()) InspectionProfileManager.getInstance()
      else InspectionProjectProfileManager.getInstance(project)
      profile = manager.getProfile(profileName)
    }
    val initialText = getProfileText(profile)

    return BooleanCommitOption.createLink(
      project, checkinHandler, disableWhenDumb = true,
      initialText,
      property,
      message("before.checkin.options.check.smells.choose.profile")) { sourceLink, linkData ->
      JBPopupMenu.showBelow(sourceLink, ActionPlaces.CODE_INSPECTION, createProfileChooser(linkData))
    }
  }

  private fun getProfileText(profile: InspectionProfileImpl?): @Nls String {
    return if (profile == null || profile == InspectionProjectProfileManager.getInstance(project).currentProfile)
      message(emptyTitleKey)
    else message(profileTitleKey, profile.displayName)
  }

  private fun createProfileChooser(linkContext: BooleanCommitOption.LinkContext): DefaultActionGroup {
    val group = DefaultActionGroup()
    group.add(Separator.create(IdeBundle.message("separator.scheme.stored.in", IdeBundle.message("scheme.project"))))
    fillActions(group, InspectionProjectProfileManager.getInstance(project), linkContext)
    group.add(Separator.create(IdeBundle.message("separator.scheme.stored.in", IdeBundle.message("scheme.ide"))))
    fillActions(group, InspectionProfileManager.getInstance(), linkContext)
    return group
  }

  private fun fillActions(group: DefaultActionGroup, manager: InspectionProfileManager, linkContext: BooleanCommitOption.LinkContext) {
    for (profile in manager.profiles) {
      group.add(object : AnAction() {
        init {
          templatePresentation.setText(profile.displayName, false)
          templatePresentation.icon = if (profileProperty.get() == profile.name) AllIcons.Actions.Checked else null
        }

        override fun actionPerformed(e: AnActionEvent) {
          profileProperty.set(profile.name)
          isLocalProperty.set(manager !is InspectionProjectProfileManager)
          linkContext.setCheckboxText(getProfileText(profile))
        }
      })
    }
  }
}

@YesNoCancelResult
private fun askReviewCommitCancel(project: Project, codeSmells: List<CodeSmellInfo>, @NlsContexts.Button commitActionText: String): Int =
  yesNoCancel(message("code.smells.error.messages.tab.name"), getDescription(codeSmells))
    .icon(getWarningIcon())
    .yesText(StringUtil.toTitleCase(message("code.smells.review.button")))
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

private class ProgressSinkIndicatorEx(
  private val reporter: RawProgressReporter?,
  private val contextModality: ModalityState,
) : AbstractProgressIndicatorExBase(), StandardProgressIndicator {

  override fun getModalityState(): ModalityState {
    return contextModality
  }

  override fun setText(text: String?) {
    reporter?.text(text = text)
  }

  override fun setText2(text: String?) {
    reporter?.details(details = text)
  }

  override fun setFraction(fraction: Double) {
    reporter?.fraction(fraction = fraction)
  }
}
