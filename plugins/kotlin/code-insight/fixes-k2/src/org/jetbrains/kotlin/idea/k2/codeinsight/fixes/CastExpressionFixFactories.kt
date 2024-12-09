// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.types.KaDefinitelyNotNullType
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaType
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

@OptIn(KaExperimentalApi::class)
object CastExpressionFixFactories {

    private data class ElementContext(
        val typePresentation: String,
        val typeSourceCode: String,
        val isDefinitelyNotNull: Boolean,
    )

    private class CastExpressionModCommandAction(
        element: PsiElement,
        elementContext: ElementContext,
    ) : KotlinPsiUpdateModCommandAction.ElementBased<PsiElement, ElementContext>(element, elementContext) {

        override fun getFamilyName(): String =
            KotlinBundle.message("fix.cast.expression.family")

        override fun getPresentation(
            context: ActionContext,
            element: PsiElement
        ): Presentation {
            val (typePresentation) = getElementContext(context, element)
            val actionName = KotlinBundle.message(
                "fix.cast.expression.text",
                getExpressionShortText(element),
                typePresentation,
            )
            return Presentation.of(actionName)
        }

        override fun invoke(
            actionContext: ActionContext,
            element: PsiElement,
            elementContext: ElementContext,
            updater: ModPsiUpdater,
        ) {
            val pattern = if (elementContext.isDefinitelyNotNull) "$0 as ($1)" else "$0 as $1"
            val expressionToInsert = KtPsiFactory(actionContext.project)
                .createExpressionByPattern(
                    pattern,
                    element,
                    elementContext.typeSourceCode,
                )
            val newExpression = element.replaced(expressionToInsert)

            shortenReferences(newExpression)
            updater.moveCaretTo(newExpression.endOffset)
        }
    }

    val smartcastImpossible = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.SmartcastImpossible ->
        val actualType = diagnostic.subject.expressionType
             ?: return@ModCommandBased emptyList()
        createFixes(diagnostic.isCastToNotNull, actualType, diagnostic.desiredType, diagnostic.psi)
    }

    val typeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.TypeMismatch ->
        createFixes(diagnostic.isMismatchDueToNullability, diagnostic.actualType, diagnostic.expectedType, diagnostic.psi)
    }

    val throwableTypeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ThrowableTypeMismatch ->
        createFixes(diagnostic.isMismatchDueToNullability, diagnostic.actualType, builtinTypes.throwable, diagnostic.psi)
    }

    val argumentTypeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ArgumentTypeMismatch ->
        createFixes(diagnostic.isMismatchDueToNullability, diagnostic.actualType, diagnostic.expectedType, diagnostic.psi)
    }

    val assignmentTypeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.AssignmentTypeMismatch ->
        createFixes(diagnostic.isMismatchDueToNullability, diagnostic.actualType, diagnostic.expectedType, diagnostic.psi)
    }

    val returnTypeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ReturnTypeMismatch ->
        createFixes(diagnostic.isMismatchDueToNullability, diagnostic.actualType, diagnostic.expectedType, diagnostic.psi)
    }

    val initializerTypeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.InitializerTypeMismatch ->
        val initializer = (diagnostic.psi as? KtProperty)?.initializer
            ?: return@ModCommandBased emptyList()

        createFixes(
            diagnostic.isMismatchDueToNullability,
            diagnostic.actualType,
            diagnostic.expectedType,
            initializer,
        )
    }

    private fun KaSession.createFixes(
        isDueToNullability: Boolean,
        actualType: KaType,
        expectedType: KaType,
        element: PsiElement,
    ): List<CastExpressionModCommandAction> {
        // `null` related issue should not be handled by a cast fix.
        if (isDueToNullability || expectedType is KaErrorType) return emptyList()

        if (element is KtExpression) {
            val actualExpressionType = element.expressionType
            if (actualExpressionType != null && !actualExpressionType.semanticallyEquals(actualType)) {
                //don't suggest cast for nested generic argument incompatibilities
                return emptyList()
            }
        }

        // Do not offer to cast to an incompatible type.
        if (!actualType.hasCommonSubtypeWith(expectedType)) {
            return emptyList()
        }

        val elementContext = ElementContext(
            expectedType.render(KaTypeRendererForSource.WITH_SHORT_NAMES, position = Variance.OUT_VARIANCE),
            expectedType.render(KaTypeRendererForSource.WITH_QUALIFIED_NAMES, position = Variance.OUT_VARIANCE),
            expectedType is KaDefinitelyNotNullType,
        )

        return listOf(
            CastExpressionModCommandAction(element, elementContext),
        )
    }
}
