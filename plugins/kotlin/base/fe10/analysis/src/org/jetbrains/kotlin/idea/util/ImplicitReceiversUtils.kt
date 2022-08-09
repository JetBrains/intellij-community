// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.util

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.findLabelAndCall
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.calls.DslMarkerUtils
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.scopes.utils.getImplicitReceiversHierarchy
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf
import java.util.*
import kotlin.collections.LinkedHashSet

fun LexicalScope.getImplicitReceiversWithInstance(excludeShadowedByDslMarkers: Boolean = false): Collection<ReceiverParameterDescriptor> {
    return getImplicitReceiversWithInstanceToExpression(excludeShadowedByDslMarkers).keys
}

interface ReceiverExpressionFactory {
    val isImmediate: Boolean
    val expressionText: String
    fun createExpression(psiFactory: KtPsiFactory, shortThis: Boolean = true): KtExpression
}

fun LexicalScope.getFactoryForImplicitReceiverWithSubtypeOf(receiverType: KotlinType): ReceiverExpressionFactory? =
    getImplicitReceiversWithInstanceToExpression().entries.firstOrNull { (receiverDescriptor, _) ->
        receiverDescriptor.type.isSubtypeOf(receiverType)
    }?.value

fun LexicalScope.getImplicitReceiversWithInstanceToExpression(
    excludeShadowedByDslMarkers: Boolean = false
): Map<ReceiverParameterDescriptor, ReceiverExpressionFactory?> {
    val allReceivers = getImplicitReceiversHierarchy()
    // We use a set to work around a bug with receiver for companion object present twice in the result of getImplicitReceiversHierarchy()
    val receivers = LinkedHashSet(
        if (excludeShadowedByDslMarkers) {
            allReceivers - getParametersShadowedByDslMarkers(allReceivers)
        } else {
            allReceivers
        }
    )

    val outerDeclarationsWithInstance = LinkedHashSet<DeclarationDescriptor>()
    var current: DeclarationDescriptor? = ownerDescriptor
    while (current != null) {
        if (current is PropertyAccessorDescriptor) {
            current = current.correspondingProperty
        }
        outerDeclarationsWithInstance.add(current)

        val classDescriptor = current as? ClassDescriptor
        if (classDescriptor != null && !classDescriptor.isInner && !DescriptorUtils.isLocal(classDescriptor)) break

        current = current.containingDeclaration
    }

    val result = LinkedHashMap<ReceiverParameterDescriptor, ReceiverExpressionFactory?>()
    for ((index, receiver) in receivers.withIndex()) {
        val owner = receiver.containingDeclaration
        if (owner is ScriptDescriptor) {
            result[receiver] = null
            outerDeclarationsWithInstance.addAll(owner.implicitReceivers)
            continue
        }

        val (expressionText, isImmediateThis) = when {
            owner in outerDeclarationsWithInstance -> {
                val thisWithLabel = getThisQualifierName(receiver)?.let { "this@${it.render()}" }
                when (index) {
                  0 -> (thisWithLabel ?: "this") to true
                  else -> thisWithLabel to false
                }
            }
            owner is ClassDescriptor && owner.kind.isSingleton -> {
                IdeDescriptorRenderers.SOURCE_CODE.renderClassifierName(owner) to false
            }
            else -> continue
        }

        result[receiver] = if (expressionText != null) createReceiverExpressionFactory(expressionText, isImmediateThis) else null
    }

    return result
}

private fun createReceiverExpressionFactory(expressionText: String, isImmediateThis: Boolean): ReceiverExpressionFactory {
    return object : ReceiverExpressionFactory {
        override val isImmediate = isImmediateThis
        override val expressionText: String get() = expressionText
        override fun createExpression(psiFactory: KtPsiFactory, shortThis: Boolean): KtExpression {
            return psiFactory.createExpression(if (shortThis && isImmediateThis) "this" else expressionText)
        }
    }
}

private fun getThisQualifierName(receiver: ReceiverParameterDescriptor): Name? {
    val descriptor = receiver.containingDeclaration
    val name = descriptor.name
    if (!name.isSpecial) {
        return name
    }

    val functionLiteral = DescriptorToSourceUtils.descriptorToDeclaration(descriptor) as? KtFunctionLiteral
    return functionLiteral?.findLabelAndCall()?.first
}

private fun getParametersShadowedByDslMarkers(receiverParameters: List<ReceiverParameterDescriptor>): Set<ReceiverParameterDescriptor> {
    val typesByDslScopes = mutableMapOf<FqName, MutableList<ReceiverParameterDescriptor>>()

    for (receiverParameter in receiverParameters) {
        val dslMarkers = DslMarkerUtils.extractDslMarkerFqNames(receiverParameter.value).all()
        for (marker in dslMarkers) {
            typesByDslScopes.getOrPut(marker) { mutableListOf() } += receiverParameter
        }
    }

    // For each DSL marker, all receivers except the closest one are shadowed by it; that is why we drop it
    return typesByDslScopes.values.flatMapTo(mutableSetOf()) { it.drop(1) }
}