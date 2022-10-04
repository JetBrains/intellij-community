// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemHighlightType
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtImplicitReceiver
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.AbstractKotlinApplicatorBasedInspection
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.*
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.renderer.render

internal class ImplicitThisInspection : AbstractKotlinApplicatorBasedInspection<KtExpression, ImplicitThisInspection.ImplicitReceiverInfo>(
    KtExpression::class
) {
    data class ImplicitReceiverInfo(
        val receiverLabel: Name?,
        val isUnambiguousLabel: Boolean
    ) : KotlinApplicatorInput

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtExpression> = ApplicabilityRanges.SELF

    override fun getInputProvider() = inputProvider { expression: KtExpression ->
        val reference = if (expression is KtCallableReferenceExpression) expression.callableReference else expression
        val declarationSymbol = reference.mainReference?.resolveToSymbol() ?: return@inputProvider null

        // Get associated class symbol on declaration-site
        val declarationAssociatedClass = getAssociatedClass(declarationSymbol) ?: return@inputProvider null

        // Getting the implicit receiver
        val allImplicitReceivers = reference.containingKtFile.getScopeContextForPosition(reference).implicitReceivers
        getImplicitReceiverInfoOfClass(allImplicitReceivers, declarationAssociatedClass)
    }

    override fun getApplicator(): KotlinApplicator<KtExpression, ImplicitReceiverInfo> = applicator<KtExpression, ImplicitReceiverInfo> {
        familyAndActionName(KotlinBundle.lazyMessage(("inspection.implicit.this.display.name")))
        isApplicableByPsi { expression ->
            when (expression) {
                is KtSimpleNameExpression -> {
                    if (expression !is KtNameReferenceExpression) return@isApplicableByPsi false
                    if (expression.parent is KtThisExpression) return@isApplicableByPsi false
                    if (expression.parent is KtCallableReferenceExpression) return@isApplicableByPsi false
                    if (expression.isSelectorOfDotQualifiedExpression()) return@isApplicableByPsi false
                    val parent = expression.parent
                    if (parent is KtCallExpression && parent.isSelectorOfDotQualifiedExpression()) return@isApplicableByPsi false
                    true
                }
                is KtCallableReferenceExpression -> expression.receiverExpression == null
                else -> false
            }
        }
        applyTo { expression, input ->
            expression.addImplicitThis(input)
        }
    }

}

private fun KtAnalysisSession.getAssociatedClass(symbol: KtSymbol): KtClassOrObjectSymbol? {
    // both variables and functions are callable and only they can be referenced by "this"
    if (symbol !is KtCallableSymbol) return null
    return when (symbol) {
        is KtFunctionSymbol, is KtPropertySymbol ->
            if (symbol.isExtension) symbol.receiverType?.expandedClassSymbol else symbol.getContainingSymbol() as? KtClassOrObjectSymbol
        is KtVariableLikeSymbol -> {
            val variableType = symbol.returnType as? KtFunctionalType
            variableType?.receiverType?.expandedClassSymbol
        }
        else -> null
    }
}

private fun KtAnalysisSession.getImplicitReceiverInfoOfClass(
    implicitReceivers: List<KtImplicitReceiver>, associatedClass: KtClassOrObjectSymbol
): ImplicitThisInspection.ImplicitReceiverInfo? {
    // We can't use "this" with label if the label is already taken
    val alreadyReservedLabels = mutableListOf<Name>()

    var isInnermostReceiver = true
    for (receiver in implicitReceivers) {
        val (receiverClass, receiverLabel) = getImplicitReceiverClassAndTag(receiver) ?: return null

        if (receiverClass == associatedClass) {
            if (receiverLabel in alreadyReservedLabels) return null
            return if (isInnermostReceiver || receiverLabel != null) ImplicitThisInspection.ImplicitReceiverInfo(
                receiverLabel,
                isInnermostReceiver
            ) else null
        }

        receiverLabel?.let { alreadyReservedLabels.add(it) }
        isInnermostReceiver = false
    }
    return null
}

private fun KtAnalysisSession.getImplicitReceiverClassAndTag(receiver: KtImplicitReceiver): Pair<KtClassOrObjectSymbol, Name?>? {
    val associatedClass = receiver.type.expandedClassSymbol ?: return null
    val associatedTag: Name? = when (val receiverSymbol = receiver.ownerSymbol) {
        is KtClassOrObjectSymbol -> receiverSymbol.name
        is KtAnonymousFunctionSymbol -> {
            val receiverPsi = receiverSymbol.psi
            val potentialLabeledPsi = receiverPsi?.parent?.parent
            if (potentialLabeledPsi is KtLabeledExpression) potentialLabeledPsi.getLabelNameAsName()
            else {
                val potentialCallExpression = potentialLabeledPsi?.parent as? KtCallExpression
                val potentialCallNameReference = (potentialCallExpression?.calleeExpression as? KtNameReferenceExpression)
                potentialCallNameReference?.getReferencedNameAsName()
            }
        }
        is KtFunctionSymbol -> receiverSymbol.name
        else -> null
    }
    return Pair(associatedClass, associatedTag)
}


private fun KtExpression.isSelectorOfDotQualifiedExpression(): Boolean {
    val parent = parent
    return parent is KtDotQualifiedExpression && parent.selectorExpression == this
}

private fun KtExpression.addImplicitThis(input: ImplicitThisInspection.ImplicitReceiverInfo) {
    val reference = if (this is KtCallableReferenceExpression) callableReference else this
    val thisExpressionText = if (input.isUnambiguousLabel) "this" else "this@${input.receiverLabel?.render()}"
    val factory = KtPsiFactory(project)
    with(reference) {
        when (parent) {
            is KtCallExpression -> parent.replace(factory.createExpressionByPattern("$0.$1", thisExpressionText, parent))
            is KtCallableReferenceExpression -> parent.replace(
                factory.createExpressionByPattern(
                    "$0::$1", thisExpressionText, this
                )
            )
            else -> this.replace(factory.createExpressionByPattern("$0.$1", thisExpressionText, this))
        }
    }
}
