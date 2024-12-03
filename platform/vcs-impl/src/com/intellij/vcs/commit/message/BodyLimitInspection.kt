// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit.message

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.formatting.LineWrappingUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.psi.PsiFile
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindIntValue
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.util.DocumentUtil
import org.jetbrains.annotations.Nls

class BodyLimitInspection : BaseCommitMessageInspection() {
  @JvmField
  var RIGHT_MARGIN: Int = 72

  override fun getDisplayName(): @Nls String {
    return VcsBundle.message("inspection.BodyLimitInspection.display.name")
  }

  override fun Panel.createOptions(project: Project, disposable: Disposable): Boolean {
    val settings = VcsConfiguration.getInstance(project)

    row(VcsBundle.message("settings.commit.message.right.margin.label")) {
      spinner(0..10000)
        .bindIntValue(::RIGHT_MARGIN)
    }
    row {
      checkBox(VcsBundle.message("settings.commit.message.show.right.margin.label"))
        .bindSelected(settings::USE_COMMIT_MESSAGE_MARGIN)
    }
    row {
      checkBox(ApplicationBundle.message("checkbox.wrap.typing.on.right.margin"))
        .bindSelected(settings::WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN)
    }
    return false
  }

  override fun checkFile(file: PsiFile, document: Document, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    val lines = 1 until document.getLineCount()
    return lines.mapNotNull { line ->
      val problemText = VcsBundle.message("commit.message.inspection.message.body.lines.should.not.exceed.characters", RIGHT_MARGIN)
      checkRightMargin(file, document, manager, isOnTheFly, line, RIGHT_MARGIN, problemText,
                       WrapLineQuickFix(), ReformatCommitMessageQuickFix())
    }.toTypedArray()
  }

  override fun canReformat(project: Project, document: Document): Boolean {
    return hasProblems(project, document)
  }

  override fun reformat(project: Project, document: Document) {
    WrapLineQuickFix().doApplyFix(project, document, null)
  }

  private inner class WrapLineQuickFix : BaseCommitMessageQuickFix() {
    override fun getFamilyName(): @IntentionFamilyName String {
      return VcsBundle.message("commit.message.intention.family.name.wrap.line")
    }

    override fun doApplyFix(project: Project, document: Document, descriptor: ProblemDescriptor?) {
      val editor = CommitMessage.getEditor(document) ?: return

      val range = if (descriptor != null && descriptor.getLineNumber() >= 0)
        DocumentUtil.getLineTextRange(document, descriptor.getLineNumber())
      else
        getBodyRange(document)

      if (!range.isEmpty) {
        wrapLines(project, editor, document, RIGHT_MARGIN, range)
      }
    }

    private fun getBodyRange(document: Document): TextRange {
      if (document.getLineCount() > 1) return TextRange.create(document.getLineStartOffset(1), document.getTextLength())
      else return TextRange.EMPTY_RANGE
    }

    private fun wrapLines(project: Project, editor: Editor, document: Document, rightMargin: Int, range: TextRange) {
      val enabledRanges = listOf(TextRange.create(0, document.textLength))
      LineWrappingUtil.doWrapLongLinesIfNecessary(editor, project, document, range.getStartOffset(), range.getEndOffset(),
                                                  enabledRanges, rightMargin)
    }
  }
}