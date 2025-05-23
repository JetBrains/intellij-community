// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.*
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

private val MAP_GET_FQ_NAME = FqName("kotlin.collections.Map.get")
private const val GET_FUNCTION_NAME = "get"
private const val DOUBLE_SPACE = "  "

class MapGetWithNotNullAssertionOperatorInspection : KotlinApplicableInspectionBase<KtPostfixExpression, Unit>() {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = postfixExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun isApplicableByPsi(element: KtPostfixExpression): Boolean =
        element.operationToken == KtTokens.EXCLEXCL && element.getReplacementData() != null

    override fun KaSession.prepareContext(element: KtPostfixExpression): Unit? =
        (element.baseExpression
            ?.resolveToCall()
            ?.successfulFunctionCallOrNull()
            ?.symbol
            ?.callableId
            ?.asSingleFqName() == MAP_GET_FQ_NAME).asUnit

    override fun InspectionManager.createProblemDescriptor(
        element: KtPostfixExpression,
        context: Unit,
        rangeInElement: TextRange?,
        onTheFly: Boolean
    ): ProblemDescriptor = createProblemDescriptor(
        element,
        rangeInElement,
        KotlinBundle.message("map.get.with.not.null.assertion.operator"),
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        onTheFly,
        ReplaceWithGetValueCallFix(),
        ReplaceWithGetOrElseFix(),
        ReplaceWithElvisErrorFix()
    )

    override fun getApplicableRanges(element: KtPostfixExpression): List<TextRange> =
        ApplicabilityRange.single(element) { it.operationReference }

    private class ReplaceWithGetValueCallFix : LocalQuickFix {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.with.get.value.call.fix.text")

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val expression = descriptor.psiElement as? KtPostfixExpression ?: return
            val (reference, index) = expression.getReplacementData() ?: return
            val replaced = expression.replaced(KtPsiFactory(project).createExpressionByPattern("$0.getValue($1)", reference, index))
            replaced.findExistingEditor()?.caretModel?.moveToOffset(replaced.endOffset)
        }
    }

    private class ReplaceWithGetOrElseFix : LocalQuickFix {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.with.get.or.else.fix.text")

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val expression = descriptor.psiElement as? KtPostfixExpression ?: return
            val (reference, index) = expression.getReplacementData() ?: return
            val replaced = expression.replaced(KtPsiFactory(project).createExpressionByPattern("$0.getOrElse($1){}", reference, index))

            val editor = replaced.findExistingEditor() ?: return
            val offset = (replaced as KtQualifiedExpression).callExpression?.lambdaArguments?.firstOrNull()?.startOffset ?: return
            val document = editor.document
            PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document)
            document.insertString(offset + 1, DOUBLE_SPACE)
            editor.caretModel.moveToOffset(offset + 2)
        }
    }

    private class ReplaceWithElvisErrorFix : LocalQuickFix {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.with.elvis.error.fix.text")

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val expression = descriptor.psiElement as? KtPostfixExpression ?: return
            val (reference, index) = expression.getReplacementData() ?: return
            val replaced = expression.replace(KtPsiFactory(project).createExpressionByPattern("$0[$1] ?: error(\"\")", reference, index))

            val editor = replaced.findExistingEditor() ?: return
            val offset = (replaced as? KtBinaryExpression)?.right?.endOffset ?: return
            editor.caretModel.moveToOffset(offset - 2)
        }
    }
}

private fun KtPostfixExpression.getReplacementData(): Pair<KtExpression, KtExpression>? {
    when (val base = baseExpression) {
        is KtQualifiedExpression -> {
            if (base.callExpression?.calleeExpression?.text != GET_FUNCTION_NAME) return null
            val reference = base.receiverExpression
            val index = base.callExpression?.valueArguments?.firstOrNull()?.getArgumentExpression() ?: return null
            return reference to index
        }

        is KtArrayAccessExpression -> {
            val reference = base.arrayExpression ?: return null
            val index = base.indexExpressions.firstOrNull() ?: return null
            return reference to index
        }

        else -> return null
    }
}
