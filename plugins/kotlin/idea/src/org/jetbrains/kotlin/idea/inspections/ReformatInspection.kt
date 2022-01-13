// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInsight.actions.VcsFacade
import com.intellij.codeInspection.*
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.formatter.FormattingChange
import org.jetbrains.kotlin.idea.formatter.FormattingChange.ReplaceWhiteSpace
import org.jetbrains.kotlin.idea.formatter.FormattingChange.ShiftIndentInsideRange
import org.jetbrains.kotlin.idea.formatter.collectFormattingChanges
import org.jetbrains.kotlin.idea.util.ProjectRootsUtil
import org.jetbrains.kotlin.psi.KtFile
import javax.swing.JComponent

class ReformatInspection(@JvmField var processChangedFilesOnly: Boolean = false) : LocalInspectionTool() {
    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (file !is KtFile || !file.isWritable || !ProjectRootsUtil.isInProjectSource(file)) {
            return null
        }

        if (processChangedFilesOnly && !VcsFacade.getInstance().hasChanges(file)) {
            return null
        }

        return collectFormattingChanges(file)
            .takeIf { it.isNotEmpty() }
            ?.mapNotNull(fun(change: FormattingChange): ProblemDescriptor? {
                val rangeOffset = when (change) {
                    is ShiftIndentInsideRange -> change.range.startOffset
                    is ReplaceWhiteSpace -> change.textRange.startOffset
                }

                val leaf = file.findElementAt(rangeOffset) ?: return null
                if (!leaf.isValid) return null
                if (leaf is PsiWhiteSpace && isEmptyLineReformat(leaf, change)) return null

                return manager.createProblemDescriptor(
                    leaf,
                    KotlinBundle.message("file.is.not.properly.formatted"),
                    ReformatQuickFix,
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    isOnTheFly
                )
            })
            ?.toTypedArray()
    }

    override fun createOptionsPanel(): JComponent = SingleCheckboxOptionsPanel(
        KotlinBundle.message("apply.only.to.modified.files.for.projects.under.a.version.control"),
        this,
        "processChangedFilesOnly",
    )

    private fun isEmptyLineReformat(whitespace: PsiWhiteSpace, change: FormattingChange): Boolean {
        if (change !is ReplaceWhiteSpace) return false

        val beforeText = whitespace.text
        val afterText = change.whiteSpace

        return beforeText.count { it == '\n' } == afterText.count { it == '\n' } &&
                beforeText.substringAfterLast('\n') == afterText.substringAfterLast('\n')
    }

    private object ReformatQuickFix : LocalQuickFix {
        override fun getFamilyName(): String = KotlinBundle.message("reformat.quick.fix.family.name")
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            CodeStyleManager.getInstance(project).reformat(descriptor.psiElement.containingFile)
        }
    }
}