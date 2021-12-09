// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.formatter.TrailingCommaVisitor
import org.jetbrains.kotlin.idea.formatter.kotlinCustomSettings
import org.jetbrains.kotlin.idea.formatter.trailingComma.TrailingCommaContext
import org.jetbrains.kotlin.idea.formatter.trailingComma.TrailingCommaHelper
import org.jetbrains.kotlin.idea.formatter.trailingComma.TrailingCommaState
import org.jetbrains.kotlin.idea.formatter.trailingComma.addTrailingCommaIsAllowedFor
import org.jetbrains.kotlin.idea.formatter.trailingCommaAllowedInModule
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.application.withPsiAttachment
import org.jetbrains.kotlin.idea.util.isComma
import org.jetbrains.kotlin.idea.util.isLineBreak
import org.jetbrains.kotlin.idea.util.leafIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments
import javax.swing.JComponent
import kotlin.properties.Delegates

class TrailingCommaInspection(
    @JvmField
    var addCommaWarning: Boolean = false
) : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : TrailingCommaVisitor() {
        override val recursively: Boolean = false
        private var useTrailingComma by Delegates.notNull<Boolean>()

        override fun process(trailingCommaContext: TrailingCommaContext) {
            val element = trailingCommaContext.ktElement
            val kotlinCustomSettings = element.containingKtFile.kotlinCustomSettings
            useTrailingComma = kotlinCustomSettings.addTrailingCommaIsAllowedFor(element)
            when (trailingCommaContext.state) {
                TrailingCommaState.MISSING, TrailingCommaState.EXISTS -> {
                    checkCommaPosition(element)
                    checkLineBreaks(element)
                }
                else -> Unit
            }

            checkTrailingComma(trailingCommaContext)
        }

        private fun checkLineBreaks(commaOwner: KtElement) {
            val first = TrailingCommaHelper.elementBeforeFirstElement(commaOwner)
            if (first?.nextLeaf(true)?.isLineBreak() == false) {
                first.nextSibling?.let {
                    registerProblemForLineBreak(commaOwner, it, ProblemHighlightType.INFORMATION)
                }
            }

            val last = TrailingCommaHelper.elementAfterLastElement(commaOwner)
            if (last?.prevLeaf(true)?.isLineBreak() == false) {
                registerProblemForLineBreak(
                    commaOwner,
                    last,
                    if (addCommaWarning) ProblemHighlightType.GENERIC_ERROR_OR_WARNING else ProblemHighlightType.INFORMATION,
                )
            }
        }

        private fun checkCommaPosition(commaOwner: KtElement) {
            for (invalidComma in TrailingCommaHelper.findInvalidCommas(commaOwner)) {
                reportProblem(
                    invalidComma,
                    KotlinBundle.message("inspection.trailing.comma.comma.loses.the.advantages.in.this.position"),
                    KotlinBundle.message("inspection.trailing.comma.fix.comma.position")
                )
            }
        }

        private fun checkTrailingComma(trailingCommaContext: TrailingCommaContext) {
            val commaOwner = trailingCommaContext.ktElement
            val trailingCommaOrLastElement = TrailingCommaHelper.trailingCommaOrLastElement(commaOwner) ?: return
            when (trailingCommaContext.state) {
                TrailingCommaState.MISSING -> {
                    if (!trailingCommaAllowedInModule(commaOwner)) return
                    reportProblem(
                        trailingCommaOrLastElement,
                        KotlinBundle.message("inspection.trailing.comma.missing.trailing.comma"),
                        KotlinBundle.message("inspection.trailing.comma.add.trailing.comma"),
                        if (addCommaWarning) ProblemHighlightType.GENERIC_ERROR_OR_WARNING else ProblemHighlightType.INFORMATION,
                    )
                }
                TrailingCommaState.REDUNDANT -> {
                    reportProblem(
                        trailingCommaOrLastElement,
                        KotlinBundle.message("inspection.trailing.comma.useless.trailing.comma"),
                        KotlinBundle.message("inspection.trailing.comma.remove.trailing.comma"),
                        ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                        checkTrailingCommaSettings = false,
                    )
                }
                else -> Unit
            }
        }

        private fun reportProblem(
            commaOrElement: PsiElement,
            @InspectionMessage message: String,
            @IntentionFamilyName fixMessage: String,
            highlightType: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            checkTrailingCommaSettings: Boolean = true,
        ) {
            val commaOwner = commaOrElement.parent as KtElement
            // case for KtFunctionLiteral, where PsiWhiteSpace after KtTypeParameterList isn't included in this list
            val problemOwner = commonParent(commaOwner, commaOrElement)
            val highlightTypeWithAppliedCondition = highlightType.applyCondition(!checkTrailingCommaSettings || useTrailingComma)
            // INFORMATION shouldn't be reported in batch mode
            if (isOnTheFly || highlightTypeWithAppliedCondition != ProblemHighlightType.INFORMATION) {
                holder.registerProblem(
                    problemOwner,
                    message,
                    highlightTypeWithAppliedCondition,
                    commaOrElement.textRangeOfCommaOrSymbolAfter.shiftLeft(problemOwner.startOffset),
                    createQuickFix(fixMessage, commaOwner),
                )
            }
        }

        private fun registerProblemForLineBreak(
            commaOwner: KtElement,
            elementForTextRange: PsiElement,
            highlightType: ProblemHighlightType,
        ) {
            val problemElement = commonParent(commaOwner, elementForTextRange)
            val highlightTypeWithAppliedCondition = highlightType.applyCondition(useTrailingComma)
            // INFORMATION shouldn't be reported in batch mode
            if (isOnTheFly || highlightTypeWithAppliedCondition != ProblemHighlightType.INFORMATION) {
                holder.registerProblem(
                    problemElement,
                    KotlinBundle.message("inspection.trailing.comma.missing.line.break"),
                    highlightTypeWithAppliedCondition,
                    TextRange.from(elementForTextRange.startOffset, 1).shiftLeft(problemElement.startOffset),
                    createQuickFix(KotlinBundle.message("inspection.trailing.comma.add.line.break"), commaOwner),
                )
            }
        }

        private fun commonParent(commaOwner: PsiElement, elementForTextRange: PsiElement): PsiElement =
            PsiTreeUtil.findCommonParent(commaOwner, elementForTextRange)
                ?: throw KotlinExceptionWithAttachments("Common parent not found")
                    .withPsiAttachment("commaOwner", commaOwner)
                    .withAttachment("commaOwnerRange", commaOwner.textRange)
                    .withPsiAttachment("elementForTextRange", elementForTextRange)
                    .withAttachment("elementForTextRangeRange", elementForTextRange.textRange)
                    .withPsiAttachment("parent", commaOwner.parent)
                    .withAttachment("parentRange", commaOwner.parent.textRange)

        private fun ProblemHighlightType.applyCondition(condition: Boolean): ProblemHighlightType = when {
            isUnitTestMode() -> ProblemHighlightType.GENERIC_ERROR_OR_WARNING
            condition -> this
            else -> ProblemHighlightType.INFORMATION
        }

        private fun createQuickFix(
            @IntentionFamilyName fixMessage: String,
            commaOwner: KtElement,
        ): LocalQuickFix = ReformatTrailingCommaFix(commaOwner, fixMessage)

        private val PsiElement.textRangeOfCommaOrSymbolAfter: TextRange
            get() {
                val textRange = textRange
                if (isComma) return textRange

                return nextLeaf()?.leafIgnoringWhitespaceAndComments(false)?.endOffset?.takeIf { it > 0 }?.let {
                    TextRange.create(it - 1, it).intersection(textRange)
                } ?: TextRange.create(textRange.endOffset - 1, textRange.endOffset)
            }
    }

    override fun createOptionsPanel(): JComponent = SingleCheckboxOptionsPanel(
        KotlinBundle.message("inspection.trailing.comma.report.also.a.missing.comma"),
        this,
        "addCommaWarning",
    )

    class ReformatTrailingCommaFix(commaOwner: KtElement, @IntentionFamilyName private val fixMessage: String) : LocalQuickFix {
        val commaOwnerPointer = commaOwner.createSmartPointer()

        override fun getFamilyName(): String = fixMessage

        override fun applyFix(project: Project, problemDescriptor: ProblemDescriptor) {
            val element = commaOwnerPointer.element ?: return
            val range = createFormatterTextRange(element)
            val settings = CodeStyleSettingsManager.getInstance(project).cloneSettings(CodeStyle.getSettings(element.containingKtFile))
            settings.kotlinCustomSettings.ALLOW_TRAILING_COMMA = true
            settings.kotlinCustomSettings.ALLOW_TRAILING_COMMA_ON_CALL_SITE = true
            CodeStyle.doWithTemporarySettings(project, settings, Runnable {
                CodeStyleManager.getInstance(project).reformatRange(element, range.startOffset, range.endOffset)
            })
        }

        private fun createFormatterTextRange(commaOwner: KtElement): TextRange {
            val startElement = TrailingCommaHelper.elementBeforeFirstElement(commaOwner) ?: commaOwner
            val endElement = TrailingCommaHelper.elementAfterLastElement(commaOwner) ?: commaOwner
            return TextRange.create(startElement.startOffset, endElement.endOffset)
        }

        override fun getFileModifierForPreview(target: PsiFile): FileModifier? {
            val element = commaOwnerPointer.element ?: return null
            return ReformatTrailingCommaFix(PsiTreeUtil.findSameElementInCopy(element, target), fixMessage)
        }
    }
}
