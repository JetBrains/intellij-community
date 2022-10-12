// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix.fixes

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KtTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.types.KtErrorType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicatorInput
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicator
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinApplicatorTargetWithInput
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.withInput
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.types.Variance

object CastExpressionFixFactories {
    class Input(val typePresentation: String, val typeSourceCode: String) : KotlinApplicatorInput

    @OptIn(KtAllowAnalysisOnEdt::class)
    val applicator = applicator<PsiElement, Input> {
        familyName(KotlinBundle.lazyMessage("fix.cast.expression.family"))
        actionName { psi, input -> KotlinBundle.message("fix.cast.expression.text", psi.text, input.typePresentation) }
        applyToWithEditorRequired { psi, input, project, editor ->
            val expressionToInsert = KtPsiFactory(psi).createExpressionByPattern("$0 as $1", psi, input.typeSourceCode)
            val newExpression = psi.replaced(expressionToInsert)

            allowAnalysisOnEdt {
                analyze(newExpression) {
                    collectPossibleReferenceShorteningsInElement(newExpression)
                }
            }.invokeShortening()
            editor.caretModel.moveToOffset(newExpression.endOffset)
        }
    }

    val smartcastImpossible = diagnosticFixFactory(KtFirDiagnostic.SmartcastImpossible::class, applicator) { diagnostic ->
        val actualType = diagnostic.subject.getKtType() ?: return@diagnosticFixFactory emptyList()
        createFix(diagnostic.isCastToNotNull, actualType, diagnostic.desiredType, diagnostic.psi)
    }
    val typeMismatch = diagnosticFixFactory(KtFirDiagnostic.TypeMismatch::class, applicator) { diagnostic ->
        createFix(diagnostic.isMismatchDueToNullability, diagnostic.actualType, diagnostic.expectedType, diagnostic.psi)
    }
    val throwableTypeMismatch = diagnosticFixFactory(KtFirDiagnostic.ThrowableTypeMismatch::class, applicator) { diagnostic ->
        createFix(diagnostic.isMismatchDueToNullability, diagnostic.actualType, builtinTypes.THROWABLE, diagnostic.psi)
    }
    val argumentTypeMismatch = diagnosticFixFactory(KtFirDiagnostic.ArgumentTypeMismatch::class, applicator) { diagnostic ->
        createFix(diagnostic.isMismatchDueToNullability, diagnostic.actualType, diagnostic.expectedType, diagnostic.psi)
    }
    val assignmentTypeMismatch = diagnosticFixFactory(KtFirDiagnostic.AssignmentTypeMismatch::class, applicator) { diagnostic ->
        createFix(diagnostic.isMismatchDueToNullability, diagnostic.actualType, diagnostic.expectedType, diagnostic.psi)
    }
    val returnTypeMismatch = diagnosticFixFactory(KtFirDiagnostic.ReturnTypeMismatch::class, applicator) { diagnostic ->
        createFix(diagnostic.isMismatchDueToNullability, diagnostic.actualType, diagnostic.expectedType, diagnostic.psi)
    }
    val initializerTypeMismatch = diagnosticFixFactory(KtFirDiagnostic.InitializerTypeMismatch::class, applicator) { diagnostic ->
        val initializer = diagnostic.psi.initializer ?: return@diagnosticFixFactory emptyList()
        createFix(
            diagnostic.isMismatchDueToNullability,
            diagnostic.actualType,
            diagnostic.expectedType,
            initializer
        )
    }

    private fun KtAnalysisSession.createFix(
        isDueToNullability: Boolean,
        actualType: KtType,
        expectedType: KtType,
        psi: PsiElement,
    ): List<KotlinApplicatorTargetWithInput<PsiElement, Input>> {
        // `null` related issue should not be handled by a cast fix.
        if (isDueToNullability || expectedType is KtErrorType) return emptyList()

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
