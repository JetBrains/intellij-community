// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections.dfa

import com.intellij.psi.SyntaxTraverser
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.findModuleDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.resolve.dataFlowValueFactory
import org.jetbrains.kotlin.idea.resolve.languageVersionSettings
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.smartcasts.IdentifierInfo
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.isNothing

internal fun isSmartCastNecessary(expr: KtExpression, value: Boolean): Boolean {
    val bindingContext: BindingContext = expr.analyze()

    val values = getStableValuesInExpression(expr, bindingContext)
    if (values.isEmpty()) return false
    val resolutionFacade = expr.getResolutionFacade()
    val factory = resolutionFacade.dataFlowValueFactory
    val moduleDescriptor = expr.findModuleDescriptor()
    val receiverCastType = values.firstOrNull { info -> info.id is IdentifierInfo.Receiver }?.type
    return getConditionScopes(expr, value)
        .asSequence()
        .flatMap { scope -> SyntaxTraverser.psiTraverser(scope) }
        .filterIsInstance(KtExpression::class.java)
        .any { e ->
            if (receiverCastType != null) {
                val receiverCast = bindingContext.get(BindingContext.IMPLICIT_RECEIVER_SMARTCAST, e)
                if (receiverCast != null && receiverCast.receiverTypes.any { (receiver, _) -> receiver.type == receiverCastType }) {
                    return@any true
                }
            }
            val type = e.getKotlinType() ?: return@any false
            val dfValue = factory.createDataFlowValue(e, type, bindingContext, moduleDescriptor)
            if (!dfValue.isStable) return@any false
            val dataFlowType = bindingContext.get(BindingContext.EXPRESSION_TYPE_INFO, e)
                ?.dataFlowInfo?.getStableTypes(dfValue, resolutionFacade.languageVersionSettings)?.singleOrNull()
                ?: type
            if (!values.any { it.id == dfValue.identifierInfo && it.type != dataFlowType }) return@any false
            // TODO: check if smart-cast is actually induced by original expression
            return@any bindingContext.get(BindingContext.SMARTCAST, e) != null
        }
}

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
                val newValue = if ((value == true) == (parent.operationToken == KtTokens.ANDAND)) null else value
                if (parent.left == expr) getConditionScopes(parent, newValue) + listOfNotNull(parent.right)
                else getConditionScopes(parent, newValue)
            }
        }
        is KtWhenConditionWithExpression ->
            when (value) {
              false -> (generateSequence(parent.nextSibling) {it.nextSibling}.filterIsInstance<KtWhenCondition>() + 
                      generateSequence(parent.parent.nextSibling) {it.nextSibling}.filterIsInstance<KtWhenEntry>()).toList()
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
                        val nothingType = thenExpression?.getKotlinType()?.isNothing() == true ||
                                elseExpression?.getKotlinType()?.isNothing() == true
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

private fun getStableValuesInExpression(
    expr: KtExpression,
    bindingContext: BindingContext
): Set<IdentifierType> {
    val resolutionFacade = expr.getResolutionFacade()
    val factory = resolutionFacade.dataFlowValueFactory
    val moduleDescriptor = expr.findModuleDescriptor()
    return SyntaxTraverser.psiTraverser(expr)
        .filter(KtExpression::class.java)
        //.filter { e -> e.parent !is KtQualifiedExpression }
        .mapNotNull { e ->
            val type = e.getKotlinType() ?: return@mapNotNull null
            val dfValue = factory.createDataFlowValue(e, type, bindingContext, moduleDescriptor)
            if (!dfValue.isStable) return@mapNotNull null
            val dataFlowType = bindingContext.get(BindingContext.EXPRESSION_TYPE_INFO, e)
                ?.dataFlowInfo?.getStableTypes(dfValue, resolutionFacade.languageVersionSettings)?.singleOrNull()
                ?: type
            IdentifierType(dfValue.identifierInfo, dataFlowType)
        }
        .toSet()
}

internal data class IdentifierType(val id: IdentifierInfo, val type: KotlinType)