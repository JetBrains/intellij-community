// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KtTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.types.KtErrorType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.utils.getExpressionShortText
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.types.Variance

object CastExpressionFixFactories {

    private data class ElementContext(
        val typePresentation: String,
        val typeSourceCode: String,
    )

    private class CastExpressionModCommandAction(
        element: PsiElement,
        elementContext: ElementContext,
    ) : KotlinPsiUpdateModCommandAction.ElementBased<PsiElement, ElementContext>(element, elementContext) {

        override fun getFamilyName(): String = KotlinBundle.message("fix.cast.expression.family")

        override fun getActionName(
            actionContext: ActionContext,
            element: PsiElement,
            elementContext: ElementContext,
        ): String = KotlinBundle.message(
            "fix.cast.expression.text",
            getExpressionShortText(element),
            elementContext.typePresentation,
        )

        override fun invoke(
            actionContext: ActionContext,
            element: PsiElement,
            elementContext: ElementContext,
            updater: ModPsiUpdater,
        ) {
            val expressionToInsert = KtPsiFactory(actionContext.project)
                .createExpressionByPattern(
                    "$0 as $1",
                    element,
                    elementContext.typeSourceCode,
                )
            val newExpression = element.replaced(expressionToInsert)

            shortenReferences(newExpression)
            updater.moveCaretTo(newExpression.endOffset)
        }
    }

    val smartcastImpossible = KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.SmartcastImpossible ->
        val actualType = diagnostic.subject.getKtType()
            ?: return@ModCommandBased emptyList()
        createFixes(diagnostic.isCastToNotNull, actualType, diagnostic.desiredType, diagnostic.psi)
    }

    val typeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.TypeMismatch ->
        createFixes(diagnostic.isMismatchDueToNullability, diagnostic.actualType, diagnostic.expectedType, diagnostic.psi)
    }

    val throwableTypeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.ThrowableTypeMismatch ->
        createFixes(diagnostic.isMismatchDueToNullability, diagnostic.actualType, builtinTypes.THROWABLE, diagnostic.psi)
    }

    val argumentTypeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.ArgumentTypeMismatch ->
        createFixes(diagnostic.isMismatchDueToNullability, diagnostic.actualType, diagnostic.expectedType, diagnostic.psi)
    }

    val assignmentTypeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.AssignmentTypeMismatch ->
        createFixes(diagnostic.isMismatchDueToNullability, diagnostic.actualType, diagnostic.expectedType, diagnostic.psi)
    }

    val returnTypeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.ReturnTypeMismatch ->
        createFixes(diagnostic.isMismatchDueToNullability, diagnostic.actualType, diagnostic.expectedType, diagnostic.psi)
    }

    val initializerTypeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.InitializerTypeMismatch ->
        val initializer = (diagnostic.psi as? KtProperty)?.initializer
            ?: return@ModCommandBased emptyList()

        createFixes(
            diagnostic.isMismatchDueToNullability,
            diagnostic.actualType,
            diagnostic.expectedType,
            initializer,
        )
    }

    context(KtAnalysisSession)
    private fun createFixes(
        isDueToNullability: Boolean,
        actualType: KtType,
        expectedType: KtType,
        element: PsiElement,
    ): List<CastExpressionModCommandAction> {
        // `null` related issue should not be handled by a cast fix.
        if (isDueToNullability || expectedType is KtErrorType) return emptyList()

        if (element is KtExpression) {
            val actualExpressionType = element.getKtType()
            if (actualExpressionType != null && !actualExpressionType.isEqualTo(actualType)) {
                //don't suggest cast for nested generic argument incompatibilities
                return emptyList()
            }
        }

        // Do not offer to cast to an incompatible type.
        if (!actualType.hasCommonSubTypeWith(expectedType)) {
            return emptyList()
        }

        val elementContext = ElementContext(
            expectedType.render(KtTypeRendererForSource.WITH_SHORT_NAMES, position = Variance.OUT_VARIANCE),
            expectedType.render(KtTypeRendererForSource.WITH_QUALIFIED_NAMES, position = Variance.OUT_VARIANCE),
        )

        return listOf(
            CastExpressionModCommandAction(element, elementContext),
        )
    }
}
