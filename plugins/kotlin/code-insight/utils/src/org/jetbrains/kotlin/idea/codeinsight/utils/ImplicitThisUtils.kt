// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtImplicitReceiver
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*

data class ImplicitReceiverInfo(
    val receiverLabel: Name?,
    val isUnambiguousLabel: Boolean
)

context(KtAnalysisSession)
fun KtExpression.getImplicitReceiverInfo(): ImplicitReceiverInfo? {
    val reference = when (this) {
        is KtSimpleNameExpression -> this
        is KtCallableReferenceExpression -> callableReference
        is KtCallExpression -> calleeExpression
        else -> null
    } ?: return null
    val declarationSymbol = reference.mainReference?.resolveToSymbol() ?: return null

    // Get associated class symbol on declaration-site
    val declarationAssociatedClass = getAssociatedClass(declarationSymbol) ?: return null

    // Getting the implicit receiver
    val allImplicitReceivers = reference.containingKtFile.getScopeContextForPosition(reference).implicitReceivers
    return getImplicitReceiverInfoOfClass(allImplicitReceivers, declarationAssociatedClass)
}

context(KtAnalysisSession)
private fun getAssociatedClass(symbol: KtSymbol): KtClassOrObjectSymbol? {
    // both variables and functions are callable, and only they can be referenced by "this"
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

context(KtAnalysisSession)
private fun getImplicitReceiverInfoOfClass(
    implicitReceivers: List<KtImplicitReceiver>, associatedClass: KtClassOrObjectSymbol
): ImplicitReceiverInfo? {
    // We can't use "this" with label if the label is already taken
    val alreadyReservedLabels = mutableListOf<Name>()

    var isInnermostReceiver = true
    for (receiver in implicitReceivers) {
        val (receiverClass, receiverLabel) = getImplicitReceiverClassAndTag(receiver) ?: return null

        if (receiverClass == associatedClass || receiverClass.isSubClassOf(associatedClass)) {
            if (receiverLabel in alreadyReservedLabels) return null
            return if (isInnermostReceiver || receiverLabel != null) ImplicitReceiverInfo(
                receiverLabel,
                isInnermostReceiver
            ) else null
        }

        receiverLabel?.let { alreadyReservedLabels.add(it) }
        isInnermostReceiver = false
    }
    return null
}

context(KtAnalysisSession)
private fun getImplicitReceiverClassAndTag(receiver: KtImplicitReceiver): Pair<KtClassOrObjectSymbol, Name?>? {
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
