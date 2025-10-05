// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaContextParameterApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaImplicitReceiver
import org.jetbrains.kotlin.analysis.api.components.containingDeclaration
import org.jetbrains.kotlin.analysis.api.components.expandedSymbol
import org.jetbrains.kotlin.analysis.api.components.isSubClassOf
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.components.scopeContext
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*

@ApiStatus.Internal
data class ImplicitReceiverInfo(
    val receiverLabel: Name?,
    val isUnambiguousLabel: Boolean,
    val receiverProvidedBy: KtDeclaration,
)


@ApiStatus.Internal
data class ExplicitReceiverInfo(
    val receiverLabel: Name?,
    val receiverProvidedBy: KtDeclaration,
)

@OptIn(KaContextParameterApi::class)
context(_: KaSession)
@ApiStatus.Internal
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

context(_: KaSession)
@ApiStatus.Internal
fun getLabelToBeReferencedByThis(symbol: KaSymbol): ExplicitReceiverInfo? {
    val (receiverProvidedBy, associatedTag) = when (symbol) {
        is KaClassSymbol -> symbol.psi to symbol.name
        is KaAnonymousFunctionSymbol -> {
            val receiverPsi = symbol.psi
            val potentialLabeledPsi = receiverPsi?.parent?.parent
            val label = if (potentialLabeledPsi is KtLabeledExpression) {
                potentialLabeledPsi.getLabelNameAsName()
            } else {
                val potentialCallExpression = potentialLabeledPsi?.parent as? KtCallExpression
                val potentialCallNameReference = (potentialCallExpression?.calleeExpression as? KtNameReferenceExpression)
                potentialCallNameReference?.getReferencedNameAsName()
            }
            receiverPsi to label
        }

        is KaCallableSymbol -> symbol.psi to symbol.name
        else -> return null
    }
    return (receiverProvidedBy as? KtDeclaration)?.let { ExplicitReceiverInfo(associatedTag, it) }
}

@OptIn(KaContextParameterApi::class)
context(_: KaSession)
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

@OptIn(KaContextParameterApi::class)
context(_: KaSession)
private fun getImplicitReceiverInfoOfClass(
    implicitReceivers: List<KaImplicitReceiver>, associatedClass: KaClassSymbol
): ImplicitReceiverInfo? {
    // We can't use "this" with label if the label is already taken
    val alreadyReservedLabels = mutableListOf<Name>()

    var isInnermostReceiver = true
    for (receiver in implicitReceivers) {
        val receiverClass = receiver.type.expandedSymbol ?: return null
        val (receiverLabel, receiverProvidedBy) = getImplicitReceiverClassAndTag(receiver) ?: return null

        if (receiverClass == associatedClass || receiverClass.isSubClassOf(associatedClass)) {
            if (receiverLabel in alreadyReservedLabels) return null
            return if (isInnermostReceiver || receiverLabel != null) {
                ImplicitReceiverInfo(
                    receiverLabel,
                    isInnermostReceiver,
                    receiverProvidedBy,
                )
            } else null
        }

        receiverLabel?.let { alreadyReservedLabels.add(it) }
        isInnermostReceiver = false
    }
    return null
}

context(_: KaSession)
private fun getImplicitReceiverClassAndTag(receiver: KaImplicitReceiver): ExplicitReceiverInfo? {
    return getLabelToBeReferencedByThis(receiver.ownerSymbol)
}
