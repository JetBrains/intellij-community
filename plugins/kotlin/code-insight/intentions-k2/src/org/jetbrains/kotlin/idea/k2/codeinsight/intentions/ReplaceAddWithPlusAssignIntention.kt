// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*

internal class ReplaceAddWithPlusAssignIntention : KotlinApplicableModCommandAction<KtDotQualifiedExpression, Unit>(
    KtDotQualifiedExpression::class,
) {

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("replace.with1")

    override fun getPresentation(context: ActionContext, element: KtDotQualifiedExpression): Presentation? {
        val calleeName = element.calleeName ?: return null
        return Presentation.of(KotlinBundle.message("replace.0.with", calleeName))
    }

    override fun isApplicableByPsi(element: KtDotQualifiedExpression): Boolean {
        if (element.callExpression?.valueArguments?.size != 1) return false
        return element.calleeName in setOf("add", "addAll")
    }

    override fun KaSession.prepareContext(element: KtDotQualifiedExpression): Unit? {
        val resolvedCall = element.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
        val partiallyAppliedSymbol = resolvedCall.partiallyAppliedSymbol
        val receiver = partiallyAppliedSymbol.dispatchReceiver ?: partiallyAppliedSymbol.extensionReceiver ?: return null
        val receiverType = receiver.type as? KaClassType ?: return null

        if (!receiverType.isSubtypeOf(StandardClassIds.MutableCollection)) return null

        val variableSymbol = element.receiverExpression.mainReference?.resolveToSymbol() as? KaVariableSymbol ?: return null
        if (!variableSymbol.isVal) return null

        return Unit
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtDotQualifiedExpression,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        element.replace(
            KtPsiFactory(element.project).createExpressionByPattern(
                "$0 += $1", element.receiverExpression,
                element.callExpression?.valueArguments?.get(0)?.getArgumentExpression() ?: return
            )
        )
    }
}

private val KtQualifiedExpression.calleeName: String?
    get() = (callExpression?.calleeExpression as? KtNameReferenceExpression)?.text
