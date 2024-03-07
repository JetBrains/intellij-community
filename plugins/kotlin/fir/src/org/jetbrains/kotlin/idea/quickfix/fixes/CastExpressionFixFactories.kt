// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.fixes

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
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicator
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicatorInput
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinApplicatorTargetWithInput
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinDiagnosticModCommandFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticModCommandFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.withInput
import org.jetbrains.kotlin.idea.codeinsight.utils.getExpressionShortText
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.types.Variance

object CastExpressionFixFactories {

    data class Input(
        val typePresentation: String,
        val typeSourceCode: String,
    ) : KotlinApplicatorInput

    private val applicator = object : KotlinApplicator.ModCommandBased<PsiElement, Input> {

        override fun getFamilyName(): String = KotlinBundle.message("fix.cast.expression.family")

        override fun getActionName(
            psi: PsiElement,
            input: Input,
        ): String = KotlinBundle.message(
            "fix.cast.expression.text",
            getExpressionShortText(psi),
            input.typePresentation,
        )

        override fun applyTo(
            psi: PsiElement,
            input: Input,
            context: ActionContext,
            updater: ModPsiUpdater,
        ) {
            val expressionToInsert = KtPsiFactory(context.project)
                .createExpressionByPattern("$0 as $1", psi, input.typeSourceCode)
            val newExpression = psi.replaced(expressionToInsert)

            shortenReferences(newExpression)
            updater.moveCaretTo(newExpression.endOffset)
        }
    }

    val smartcastImpossible: KotlinDiagnosticModCommandFixFactory<KtFirDiagnostic.SmartcastImpossible> = diagnosticModCommandFixFactory(KtFirDiagnostic.SmartcastImpossible::class, applicator) { diagnostic ->
        val actualType = diagnostic.subject.getKtType() ?: return@diagnosticModCommandFixFactory emptyList()
        createFix(diagnostic.isCastToNotNull, actualType, diagnostic.desiredType, diagnostic.psi)
    }
    val typeMismatch: KotlinDiagnosticModCommandFixFactory<KtFirDiagnostic.TypeMismatch> = diagnosticModCommandFixFactory(KtFirDiagnostic.TypeMismatch::class, applicator) { diagnostic ->
        createFix(diagnostic.isMismatchDueToNullability, diagnostic.actualType, diagnostic.expectedType, diagnostic.psi)
    }
    val throwableTypeMismatch: KotlinDiagnosticModCommandFixFactory<KtFirDiagnostic.ThrowableTypeMismatch> = diagnosticModCommandFixFactory(KtFirDiagnostic.ThrowableTypeMismatch::class, applicator) { diagnostic ->
        createFix(diagnostic.isMismatchDueToNullability, diagnostic.actualType, builtinTypes.THROWABLE, diagnostic.psi)
    }
    val argumentTypeMismatch: KotlinDiagnosticModCommandFixFactory<KtFirDiagnostic.ArgumentTypeMismatch> = diagnosticModCommandFixFactory(KtFirDiagnostic.ArgumentTypeMismatch::class, applicator) { diagnostic ->
        createFix(diagnostic.isMismatchDueToNullability, diagnostic.actualType, diagnostic.expectedType, diagnostic.psi)
    }
    val assignmentTypeMismatch: KotlinDiagnosticModCommandFixFactory<KtFirDiagnostic.AssignmentTypeMismatch> = diagnosticModCommandFixFactory(KtFirDiagnostic.AssignmentTypeMismatch::class, applicator) { diagnostic ->
        createFix(diagnostic.isMismatchDueToNullability, diagnostic.actualType, diagnostic.expectedType, diagnostic.psi)
    }
    val returnTypeMismatch: KotlinDiagnosticModCommandFixFactory<KtFirDiagnostic.ReturnTypeMismatch> = diagnosticModCommandFixFactory(KtFirDiagnostic.ReturnTypeMismatch::class, applicator) { diagnostic ->
        createFix(diagnostic.isMismatchDueToNullability, diagnostic.actualType, diagnostic.expectedType, diagnostic.psi)
    }
    val initializerTypeMismatch: KotlinDiagnosticModCommandFixFactory<KtFirDiagnostic.InitializerTypeMismatch> = diagnosticModCommandFixFactory(KtFirDiagnostic.InitializerTypeMismatch::class, applicator) { diagnostic ->
        val initializer = (diagnostic.psi as? KtProperty)?.initializer ?: return@diagnosticModCommandFixFactory emptyList()
        createFix(
            diagnostic.isMismatchDueToNullability,
            diagnostic.actualType,
            diagnostic.expectedType,
            initializer
        )
    }

    context(KtAnalysisSession)
    private fun createFix(
        isDueToNullability: Boolean,
        actualType: KtType,
        expectedType: KtType,
        psi: PsiElement,
    ): List<KotlinApplicatorTargetWithInput<PsiElement, Input>> {
        // `null` related issue should not be handled by a cast fix.
        if (isDueToNullability || expectedType is KtErrorType) return emptyList()

        if (psi is KtExpression) {
            val actualExpressionType = psi.getKtType()
            if (actualExpressionType != null && !(actualExpressionType isEqualTo actualType)) {
                //don't suggest cast for nested generic argument incompatibilities
                return emptyList()
            }
        }

        // Do not offer to cast to an incompatible type.
        if (!actualType.hasCommonSubTypeWith(expectedType)) {
            return emptyList()
        }
        return listOf(
            psi withInput Input(
                expectedType.render(KtTypeRendererForSource.WITH_SHORT_NAMES, position = Variance.OUT_VARIANCE),
                expectedType.render(KtTypeRendererForSource.WITH_QUALIFIED_NAMES, position = Variance.OUT_VARIANCE)
            )
        )
    }
}
