// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.dfa

import com.intellij.psi.SyntaxTraverser
import org.jetbrains.kotlin.analysis.api.KaNonPublicApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaSmartCastedReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

context(KaSession)
@OptIn(KaNonPublicApi::class)
internal fun isSmartCastNecessary(expr: KtExpression, value: Boolean): Boolean {
    val values = getValuesInExpression(expr)
    if (values.isEmpty()) return false
    return getConditionScopes(expr, value)
        .asSequence()
        .flatMap { scope -> SyntaxTraverser.psiTraverser(scope) }
        .filterIsInstance(KtExpression::class.java)
        .any { e ->
            if (e is KtReferenceExpression) {
                val info = e.smartCastInfo
                if (info != null) {
                    val expectedType = (if (e.parent is KtThisExpression) e.parent else e).expectedType
                    val ktType = values[e.mainReference.resolveToSymbol()]
                    return@any ktType != null && !info.smartCastType.semanticallyEquals(ktType)
                            && (expectedType == null || !ktType.isSubtypeOf(expectedType))
                }
            }

            val implicitReceiverSmartCastList = e.implicitReceiverSmartCasts
            if (implicitReceiverSmartCastList.isNotEmpty()) {
                val symbol = e.resolveToCall()?.singleCallOrNull<KaCallableMemberCall<*, *>>()?.partiallyAppliedSymbol
                if (symbol != null) {
                    var receiver = symbol.dispatchReceiver ?: symbol.extensionReceiver
                    if (receiver is KaSmartCastedReceiverValue) {
                        receiver = receiver.original
                    }
                    if (receiver is KaImplicitReceiverValue) {
                        val ktType = values[receiver.symbol]
                        return@any ktType != null
                                && implicitReceiverSmartCastList.none { smartCast -> smartCast.type.semanticallyEquals(ktType) }
                    }
                }
            }
            return@any false
        }
}

context(KaSession)
private fun getValuesInExpression(expr: KtExpression): Map<KaSymbol, KaType> {
    val map = hashMapOf<KaSymbol, KaType>()
    SyntaxTraverser.psiTraverser(expr)
        .filter(KtReferenceExpression::class.java)
        .forEach { e ->
            val symbol = e.mainReference.resolveToSymbol()
            if (symbol != null) {
                val type = e.expressionType
                if (type != null) {
                    map[symbol] = type
                }

            }
        }
    return map

}


context(KaSession)
private fun getConditionScopes(expr: KtExpression, value: Boolean?): List<KtElement> {
    // TODO: reuse more standard utility to collect scopes
    return when (val parent = expr.parent) {
        is KtPrefixExpression ->
            if (parent.operationToken == KtTokens.EXCL) {
                getConditionScopes(parent, if (value == null) null else !value)
            } else {
                emptyList()
            }

        is KtParenthesizedExpression ->
            getConditionScopes(parent, value)

        is KtBinaryExpression -> {
            if (parent.operationToken != KtTokens.ANDAND && parent.operationToken != KtTokens.OROR) emptyList()
            else {
                val newValue = if ((value == true) == (parent.operationToken == KtTokens.OROR)) null else value
                if (parent.left == expr) getConditionScopes(parent, newValue) + listOfNotNull(parent.right)
                else getConditionScopes(parent, newValue)
            }
        }

        is KtWhenConditionWithExpression ->
            when (value) {
                false -> (generateSequence(parent.nextSibling) { it.nextSibling }.filterIsInstance<KtWhenCondition>() +
                        generateSequence(parent.parent.nextSibling) { it.nextSibling }.filterIsInstance<KtWhenEntry>()).toList()

                true -> listOfNotNull((parent.parent as? KtWhenEntry)?.expression)
                else -> emptyList()
            }

        is KtContainerNode ->
            when (val gParent = parent.parent) {
                is KtIfExpression ->
                    if (gParent.condition == expr) {
                        val thenExpression = gParent.then
                        val elseExpression = gParent.`else`
                        val result = mutableListOf<KtExpression>()
                        if (thenExpression != null && value != false) result += thenExpression
                        if (elseExpression != null && value != true) result += elseExpression
                        val nothingType = thenExpression?.getKotlinType()?.isNothingType == true ||
                                elseExpression?.getKotlinType()?.isNothingType == true
                        if (nothingType) {
                            var next = gParent.nextSibling
                            while (next != null) {
                                if (next is KtExpression) result += next
                                next = next.nextSibling
                            }
                        }
                        result
                    } else emptyList()

                is KtWhileExpression ->
                    if (gParent.condition == expr && value != false) {
                        listOfNotNull(gParent.body)
                    } else {
                        emptyList()
                    }

                else -> emptyList()
            }

        else -> emptyList()
    }
}
