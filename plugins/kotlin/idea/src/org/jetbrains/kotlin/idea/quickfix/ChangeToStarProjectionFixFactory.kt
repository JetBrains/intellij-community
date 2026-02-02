// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.psi.typeArguments
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.quickfix.StarProjectionUtils.isAsKeyword
import org.jetbrains.kotlin.idea.quickfix.StarProjectionUtils.isOnJvm
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.kotlin.psi.KtProjectionKind
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypes
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsExpression
import org.jetbrains.kotlin.resolve.calls.model.ArgumentMatch
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.checker.SimpleClassicTypeSystemContext.isArrayOrNullableArray
import org.jetbrains.kotlin.types.typeUtil.isTypeParameter

internal object ChangeToStarProjectionFixFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val psiElement = diagnostic.psiElement

        // We don't suggest this quick-fix for array instance checks because there is ConvertToIsArrayOfCallFix
        if (psiElement.parent is KtIsExpression && diagnostic.isArrayInstanceCheck() && psiElement.isOnJvm()) return null

        val (binaryExpr, typeReference, typeElement) = StarProjectionUtils.getChangeToStarProjectionFixInfo(psiElement) ?: return null

        if (binaryExpr?.operationReference?.isAsKeyword() == true) {
            val parent = binaryExpr.getParentOfTypes(
                true,
                KtValueArgument::class.java,
                KtQualifiedExpression::class.java,
                KtCallableDeclaration::class.java
            )
            if (parent is KtCallableDeclaration
                && parent.typeReference.typeArguments().any { it.projectionKind != KtProjectionKind.STAR }
                && typeReference.typeArguments().isNotEmpty()
                && binaryExpr.isUsedAsExpression(binaryExpr.analyze(BodyResolveMode.PARTIAL_WITH_CFA))
            ) return null
            val type = when (parent) {
                is KtValueArgument -> {
                    val callExpr = parent.getStrictParentOfType<KtCallExpression>()
                    (callExpr?.resolveToCall()?.getArgumentMapping(parent) as? ArgumentMatch)?.valueParameter?.original?.type
                }
                is KtQualifiedExpression ->
                    if (KtPsiUtil.safeDeparenthesize(parent.receiverExpression) == binaryExpr)
                        parent.resolveToCall()?.resultingDescriptor?.extensionReceiverParameter?.value?.original?.type
                    else
                        null
                else ->
                    null
            }
            if (type?.arguments?.any { !it.isStarProjection && !it.type.isTypeParameter() } == true) return null
        }

        if (typeElement.typeArgumentsAsTypes.isNotEmpty()) {
            return ChangeToStarProjectionFix(typeElement).asIntention()
        }
        return null
    }

    private fun Diagnostic.isArrayInstanceCheck(): Boolean =
        factory == Errors.CANNOT_CHECK_FOR_ERASED && Errors.CANNOT_CHECK_FOR_ERASED.cast(this).a.isArrayOrNullableArray()
}
