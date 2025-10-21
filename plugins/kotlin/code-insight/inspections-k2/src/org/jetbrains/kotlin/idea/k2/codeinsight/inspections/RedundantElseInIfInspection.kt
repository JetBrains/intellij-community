// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.isUsedAsExpression
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.adjustLineIndent
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespace
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal class RedundantElseInIfInspection : KotlinApplicableInspectionBase.Simple<KtIfExpression, Unit>() {

    override fun createQuickFix(
        element: KtIfExpression,
        context: Unit,
    ): KotlinModCommandQuickFix<KtIfExpression> = object : KotlinModCommandQuickFix<KtIfExpression>() {

        override fun getFamilyName(): String =
            KotlinBundle.message("remove.redundant.else.fix.text")

        override fun applyFix(
            project: Project,
            element: KtIfExpression,
            updater: ModPsiUpdater,
        ) {
            val elseKeyword = element.lastSingleElseKeyword() ?: return
            val elseExpression = elseKeyword.getStrictParentOfType<KtIfExpression>()?.`else` ?: return

            val copy = elseExpression.copy()
            if (copy is KtBlockExpression) {
                copy.lBrace?.delete()
                copy.rBrace?.delete()
            }
            val parent = element.parent
            val added = parent.addAfter(copy, element)

            if (added.getLineNumber() == elseKeyword.getLineNumber()) {
                parent.addAfter(KtPsiFactory(project).createNewLine(), element)
            }

            elseExpression.parent.delete()
            elseKeyword.delete()

            element.containingFile.adjustLineIndent(
                element.endOffset,
                (added.getNextSiblingIgnoringWhitespace() ?: added.parent).endOffset,
            )
        }
    }

    override fun getProblemDescription(
        element: KtIfExpression,
        context: Unit,
    ): String = KotlinBundle.message("redundant.else")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ) = object : KtVisitorVoid() {

        override fun visitIfExpression(expression: KtIfExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun getApplicableRanges(element: KtIfExpression): List<TextRange> {
        val textRange = element.lastSingleElseKeyword()
            ?.textRange
            ?.shiftRight(-element.startOffset)
        return listOfNotNull(textRange)
    }

    override fun isApplicableByPsi(element: KtIfExpression): Boolean {
        if (element.elseKeyword == null || element.isElseIf()) return false
        element.lastSingleElseKeyword() ?: return false

        return true
    }

    override fun KaSession.prepareContext(element: KtIfExpression): Unit? {
        if (element.hasRedundantElse()) {
            return Unit
        }

        return null
    }

    private fun KtExpression.isElseIf() = parent.node.elementType == KtNodeTypes.ELSE

    private fun KtIfExpression.lastSingleElseKeyword(): PsiElement? {
        var ifExpression = this
        while (true) {
            ifExpression = ifExpression.`else` as? KtIfExpression ?: break
        }
        return ifExpression.elseKeyword
    }

    context(_: KaSession)
    private fun KtIfExpression.hasRedundantElse(): Boolean {
        var ifExpression = this
        if (ifExpression.isUsedAsExpression) {
            return false
        }

        while (true) {
            if (ifExpression.then?.isReturnOrNothing() != true) return false
            ifExpression = ifExpression.`else` as? KtIfExpression ?: break
        }
        return true
    }

    private fun KtExpression.isReturnOrNothing(): Boolean {
        val lastExpression = (this as? KtBlockExpression)?.statements?.lastOrNull() ?: this
        return analyze(lastExpression) {
            lastExpression.expressionType?.isNothingType == true
        }
    }
}
