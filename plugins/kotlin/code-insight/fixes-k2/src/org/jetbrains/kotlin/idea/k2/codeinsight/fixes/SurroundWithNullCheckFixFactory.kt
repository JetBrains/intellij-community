// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.utils.SurroundWithNullCheckUtils
import org.jetbrains.kotlin.idea.codeinsight.utils.SurroundWithNullCheckUtils.hasAcceptableParent
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypesAndPredicate

internal object SurroundWithNullCheckFixFactory {

    val argumentTypeMismatchFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.ArgumentTypeMismatch ->
        if (!diagnostic.isMismatchDueToNullability) return@ModCommandBased emptyList()
        createQuickFixIfApplicableToTypeMismatch(diagnostic)
    }

    val assignmentTypeMismatchFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.AssignmentTypeMismatch ->
        if (!diagnostic.isMismatchDueToNullability) return@ModCommandBased emptyList()
        createQuickFixIfApplicableToTypeMismatch(diagnostic)
    }

    val iteratorOnNullableFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.IteratorOnNullable ->
        val nullableExpression = diagnostic.psi as? KtReferenceExpression ?: return@ModCommandBased emptyList()
        val forExpression = SurroundWithNullCheckUtils.getForExpressionIfApplicable(nullableExpression)
            ?: return@ModCommandBased emptyList()
        val elementContext = ElementContext(nullableExpression.createSmartPointer())
        listOf(
            SurroundWithNullCheckFix(forExpression, elementContext)
        )
    }

    val nullabilityMismatchBasedOnJavaAnnotationsFactory =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.NullabilityMismatchBasedOnJavaAnnotations ->
            createQuickFixIfApplicableToTypeMismatch(diagnostic)
        }

    val receiverNullabilityMismatchBasedOnJavaAnnotationsFactory =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.ReceiverNullabilityMismatchBasedOnJavaAnnotations ->
            createQuickFixIfApplicableToUnsafeCall(diagnostic)
        }

    val unsafeCallFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.UnsafeCall ->
        createQuickFixIfApplicableToUnsafeCall(diagnostic)
    }

    val unsafeImplicitInvokeCallFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.UnsafeImplicitInvokeCall ->
        createQuickFixIfApplicableToUnsafeCall(diagnostic)
    }

    val unsafeInfixCallFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.UnsafeInfixCall ->
        createQuickFixIfApplicableToUnsafeCall(diagnostic)
    }

    val unsafeOperatorCallFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.UnsafeOperatorCall ->
        createQuickFixIfApplicableToUnsafeCall(diagnostic)
    }

    context(KtAnalysisSession)
    private fun createQuickFixIfApplicableToUnsafeCall(diagnostic: KtFirDiagnostic<*>): List<SurroundWithNullCheckFix> {
        val element = when (diagnostic) {
            is KtFirDiagnostic.UnsafeCall -> diagnostic.receiverExpression
            else -> diagnostic.psi
        } ?: return emptyList()

        val expressionParent = element.getParentOfType<KtExpression>(strict = element is KtOperationReferenceExpression)
            ?: return emptyList()

        val nullableExpression = SurroundWithNullCheckUtils.getNullableExpressionIfApplicable(element) ?: return emptyList()

        val expressionTarget = expressionParent.getParentOfTypesAndPredicate(
            strict = false, parentClasses = arrayOf(KtExpression::class.java)
        ) {
            !it.isUsedAsExpression() && it.hasAcceptableParent()
        } ?: return emptyList()
        // Surround declaration (even of local variable) with null check is generally a bad idea
        if (expressionTarget is KtDeclaration) return emptyList()

        val referenceSymbol = nullableExpression.mainReference.resolveToSymbol() as? KtNamedSymbol ?: return emptyList()
        val file = expressionTarget.containingKtFile
        val scope = file.getScopeContextForPosition(expressionTarget).getCompositeScope()

        val callableSymbol = scope.getCallableSymbols(referenceSymbol.name).firstOrNull() ?: return emptyList()
        if (referenceSymbol != callableSymbol) return emptyList()

        val elementContext = ElementContext(nullableExpression.createSmartPointer())
        return listOf(
            SurroundWithNullCheckFix(expressionTarget, elementContext)
        )
    }

    context(KtAnalysisSession)
    private fun createQuickFixIfApplicableToTypeMismatch(diagnostic: KtFirDiagnostic<*>): List<SurroundWithNullCheckFix> {
        val nullableExpression = diagnostic.psi as? KtReferenceExpression ?: return emptyList()
        val root = SurroundWithNullCheckUtils.getRootExpressionIfApplicable(nullableExpression) ?: return emptyList()
        val elementContext = ElementContext(nullableExpression.createSmartPointer())
        return listOf(
            SurroundWithNullCheckFix(root, elementContext)
        )
    }

    private data class ElementContext(
        val nullableExpressionPointer: SmartPsiElementPointer<KtExpression>,
    )

    private class SurroundWithNullCheckFix(
        element: KtExpression,
        elementContext: ElementContext,
    ) : KotlinPsiUpdateModCommandAction.ElementBased<KtExpression, ElementContext>(element, elementContext) {

        override fun invoke(
            actionContext: ActionContext,
            element: KtExpression,
            elementContext: ElementContext,
            updater: ModPsiUpdater,
        ) {
            val nullableExpression = elementContext.nullableExpressionPointer.element?.let(updater::getWritable) ?: return
            SurroundWithNullCheckUtils.applyTo(actionContext.project, element, nullableExpression)
        }

        override fun getFamilyName(): String {
            return KotlinBundle.message("surround.with.null.check")
        }
    }
}
