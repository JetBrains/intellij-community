// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaImplicitReceiver
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*

data class ImplicitReceiverInfo(
    val receiverLabel: Name?,
    val isUnambiguousLabel: Boolean
)

context(KaSession)
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
    val allImplicitReceivers = reference.containingKtFile.scopeContext(reference).implicitReceivers
    return getImplicitReceiverInfoOfClass(allImplicitReceivers, declarationAssociatedClass)
}

context(KaSession)
private fun getAssociatedClass(symbol: KaSymbol): KaClassSymbol? {
    // both variables and functions are callable, and only they can be referenced by "this"
    if (symbol !is KaCallableSymbol) return null
    return when (symbol) {
        is KaNamedFunctionSymbol, is KaPropertySymbol ->
            if (symbol.isExtension) symbol.receiverType?.expandedSymbol else symbol.containingDeclaration as? KaClassSymbol
        is KaVariableSymbol -> {
            val variableType = symbol.returnType as? KaFunctionType
            variableType?.receiverType?.expandedSymbol
        }
        else -> null
    }
}

context(KaSession)
private fun getImplicitReceiverInfoOfClass(
    implicitReceivers: List<KaImplicitReceiver>, associatedClass: KaClassSymbol
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

context(KaSession)
private fun getImplicitReceiverClassAndTag(receiver: KaImplicitReceiver): Pair<KaClassSymbol, Name?>? {
    val associatedClass = receiver.type.expandedSymbol ?: return null
    val associatedTag: Name? = when (val receiverSymbol = receiver.ownerSymbol) {
        is KaClassSymbol -> receiverSymbol.name
        is KaAnonymousFunctionSymbol -> {
            val receiverPsi = receiverSymbol.psi
            val potentialLabeledPsi = receiverPsi?.parent?.parent
            if (potentialLabeledPsi is KtLabeledExpression) potentialLabeledPsi.getLabelNameAsName()
            else {
                val potentialCallExpression = potentialLabeledPsi?.parent as? KtCallExpression
                val potentialCallNameReference = (potentialCallExpression?.calleeExpression as? KtNameReferenceExpression)
                potentialCallNameReference?.getReferencedNameAsName()
            }
        }
        is KaNamedFunctionSymbol -> receiverSymbol.name
        else -> null
    }
    return Pair(associatedClass, associatedTag)
}
