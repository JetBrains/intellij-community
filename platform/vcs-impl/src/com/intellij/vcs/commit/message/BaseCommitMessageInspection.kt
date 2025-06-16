// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit.message

import com.intellij.codeInsight.daemon.impl.IntentionActionFilter
import com.intellij.codeInsight.intention.EmptyIntentionAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInspection.*
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ShortcutProvider
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.dsl.builder.Panel
import com.intellij.util.IncorrectOperationException
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import org.jetbrains.annotations.Nls

abstract class BaseCommitMessageInspection : LocalInspectionTool() {
  override fun getGroupDisplayName(): @Nls String {
    return VcsBundle.message("commit.message")
  }

  override fun getStaticDescription(): String? {
    return ""
  }

  override fun isSuppressedFor(element: PsiElement): Boolean {
    return !CommitMessage.isCommitMessage(element)
  }

  /**
   * @return whether the default options shall be used
   */
  @ApiStatus.OverrideOnly
  open fun Panel.createOptions(project: Project, disposable: Disposable): Boolean {
    val ui = createOptionsConfigurable() ?: return true
    if (ui is Disposable) Disposer.register(disposable, ui)

    row {
      cell(ui.component)
        .onApply { ui.apply(project) }
        .onReset { ui.reset(project) }
        .onIsModified { ui.isModified(project) }
    }
    return false
  }

  @ApiStatus.OverrideOnly
  @Deprecated("Implement {@link #createOptions} instead")
  @ScheduledForRemoval
  open fun createOptionsConfigurable(): ConfigurableUi<Project>? {
    return null
  }

  override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    val document = getDocument(file) ?: return null
    return checkFile(file, document, manager, isOnTheFly)
  }

  protected open fun checkFile(file: PsiFile, document: Document, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    return null
  }

  open fun canReformat(project: Project, document: Document): Boolean {
    return false
  }

  open fun reformat(project: Project, document: Document) {
  }

  protected open fun hasProblems(project: Project, document: Document): Boolean {
    val file = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return false
    val problems = checkFile(file, document, InspectionManager.getInstance(project), false)
    return problems != null && !problems.isEmpty()
  }

  class EmptyIntentionActionFilter : IntentionActionFilter {
    override fun accept(intentionAction: IntentionAction, psiFile: PsiFile?): Boolean {
      return psiFile == null || !CommitMessage.isCommitMessage(psiFile) || (intentionAction !is EmptyIntentionAction)
    }
  }

  protected abstract class BaseCommitMessageQuickFix : LocalQuickFix {
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val document = getDocument(descriptor.getPsiElement()) ?: return
      doApplyFix(project, document, descriptor)
    }

    abstract fun doApplyFix(project: Project, document: Document, descriptor: ProblemDescriptor?)
  }

  protected open class ReformatCommitMessageQuickFix : BaseCommitMessageQuickFix(), LowPriorityAction, IntentionAction, ShortcutProvider {
    override fun getFamilyName(): @IntentionFamilyName String {
      return VcsBundle.message("commit.message.intention.family.name.reformat.commit.message")
    }

    override fun doApplyFix(project: Project, document: Document, descriptor: ProblemDescriptor?) {
      ReformatCommitMessageAction.reformat(project, document)
    }

    override fun getShortcut(): ShortcutSet? {
      return KeymapUtil.getActiveKeymapShortcuts("Vcs.ReformatCommitMessage")
    }

    override fun getText(): @Nls String {
      return getName()
    }

    override fun isAvailable(project: Project, editor: Editor?, psiFile: PsiFile?): Boolean {
      return true
    }

    @Throws(IncorrectOperationException::class)
    override fun invoke(project: Project, editor: Editor?, psiFile: PsiFile) {
      val document = getDocument(psiFile) ?: return
      doApplyFix(project, document, null)
    }

    override fun startInWriteAction(): Boolean {
      return true
    }
  }

  companion object {
    @JvmStatic
    protected fun checkRightMargin(
      file: PsiFile, document: Document, manager: InspectionManager, isOnTheFly: Boolean,
      line: Int, rightMargin: Int, problemText: @InspectionMessage String, vararg fixes: LocalQuickFix,
    ): ProblemDescriptor? {
      val start = document.getLineStartOffset(line)
      val end = document.getLineEndOffset(line)

      if (end > start + rightMargin) {
        val exceedingRange = TextRange(start + rightMargin, end)
        return manager.createProblemDescriptor(file, exceedingRange, problemText, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly,
                                               *fixes)
      }
      return null
    }

    private fun getDocument(element: PsiElement): Document? {
      return PsiDocumentManager.getInstance(element.getProject()).getDocument(element.getContainingFile())
    }
  }
}