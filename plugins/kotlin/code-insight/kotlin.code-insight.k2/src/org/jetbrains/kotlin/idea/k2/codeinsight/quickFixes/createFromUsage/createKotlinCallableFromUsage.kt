// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.lang.jvm.actions.EP_NAME
import com.intellij.lang.jvm.actions.groupActionsByType
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.symbols.KtClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * This function uses [buildRequestsAndActions] below to prepare requests for creating Kotlin callables from usage in Kotlin
 * and create `IntentionAction`s for the requests.
 */
internal fun generateCreateKotlinCallableActions(element: KtElement): List<IntentionAction> {
    val callExpression = getCallExpressionToCreate(element) ?: return emptyList()
    val calleeExpression = callExpression.calleeExpression as? KtSimpleNameExpression ?: return emptyList()
    if (!calleeExpression.referenceNameOfElement()) return emptyList()
    return buildRequestsAndActions(callExpression)
}

private fun getCallExpressionToCreate(element: KtElement): KtCallExpression? {
    if (element.isPartOfImportDirectiveOrAnnotation()) return null
    val parent = element.parent
    return if (parent is KtCallExpression && parent.calleeExpression == element) parent else null
}

private fun KtSimpleNameExpression.referenceNameOfElement(): Boolean = getReferencedNameElementType() == KtTokens.IDENTIFIER

private fun buildRequestsAndActions(callExpression: KtCallExpression): List<IntentionAction> {
    val methodRequests = buildRequests(callExpression)
    val extensions = EP_NAME.extensions
    return methodRequests.flatMap { (clazz, request) ->
        extensions.flatMap { ext ->
            ext.createAddMethodActions(clazz, request)
        }
    }.groupActionsByType(KotlinLanguage.INSTANCE)
}

internal fun buildRequests(callExpression: KtCallExpression): Map<JvmClass, CreateMethodRequest> {
    val requests = LinkedHashMap<JvmClass, CreateMethodRequest>()
    val calleeExpression = callExpression.calleeExpression as? KtSimpleNameExpression ?: return requests
    val receiverExpression = calleeExpression.getReceiverExpression()

    // Register default create-from-usage request.
    analyze(callExpression) {
        // TODO: Check whether this class or file can be edited (Use `canRefactor()`).
        val defaultClassForReceiverOrFile = calleeExpression.getReceiverOrContainerClass()
        defaultClassForReceiverOrFile?.let {
            requests[it] = CreateKotlinCallableFromKotlinUsageRequest(callExpression, mutableSetOf(), receiverExpression)
        }
    }

    // Register create-abstract/extension-callable-from-usage request.
    val abstractContainerClass = analyze(callExpression) {
        val abstractTypeOfContainer = calleeExpression.getAbstractTypeOfReceiver()
        abstractTypeOfContainer?.convertToClass()
    }
    when {
        abstractContainerClass != null -> requests.registerCreateAbstractCallableFromUsage(
            abstractContainerClass, callExpression, receiverExpression
        )

        receiverExpression != null -> requests.registerCreateExtensionCallableFromUsage(
            callExpression, calleeExpression, receiverExpression
        )
    }
    return requests
}

/**
 * Returns the type of the class containing this [KtSimpleNameExpression] if the class is abstract. Otherwise, returns null.
 */
context (KtAnalysisSession)
private fun KtSimpleNameExpression.getAbstractTypeOfContainingClass(): KtType? {
    val containingClass = getStrictParentOfType<KtClassOrObject>() as? KtClass ?: return null
    if (containingClass is KtEnumEntry || containingClass.isAnnotation()) return null

    val classSymbol = containingClass.getSymbol() as? KtClassOrObjectSymbol ?: return null
    val classType = buildClassType(classSymbol)
    if (containingClass.modifierList.hasAbstractModifier() || classSymbol.classKind == KtClassKind.INTERFACE) return classType

    // KtType.getAbstractSuperType() does not guarantee it's the closest abstract super type. We can implement it as a
    // breadth first search, but it can cost a lot in terms of the memory usage.
    return classType.getAbstractSuperType()
}

context (KtAnalysisSession)
private fun KtType.getAbstractSuperType(): KtType? {
    fun List<KtType>.firstAbstractEditableType() = firstOrNull { it.hasAbstractDeclaration() && it.canRefactor() }
    return getDirectSuperTypes().firstAbstractEditableType() ?: getAllSuperTypes().firstAbstractEditableType()
}

/**
 * Returns class or super class of the express's type if the class or the super class is abstract. Otherwise, returns null.
 */
context (KtAnalysisSession)
private fun KtExpression.getTypeOfAbstractSuperClass(): KtType? {
    val type = getKtType() ?: return null
    if (type.hasAbstractDeclaration()) return type
    return type.getAllSuperTypes().firstOrNull { it.hasAbstractDeclaration() }
}

/**
 * Returns the receiver's type if it is abstract, or it has an abstract super class. Otherwise, returns null.
 */
context (KtAnalysisSession)
private fun KtSimpleNameExpression.getAbstractTypeOfReceiver(): KtType? {
    // If no explicit receiver exists, the containing class can be an implicit receiver.
    val receiver = getReceiverExpression() ?: return getAbstractTypeOfContainingClass()
    return receiver.getTypeOfAbstractSuperClass()
}

private fun LinkedHashMap<JvmClass, CreateMethodRequest>.registerCreateAbstractCallableFromUsage(
    abstractContainerClass: KtClass, callExpression: KtCallExpression, receiverExpression: KtExpression?
) = abstractContainerClass.toLightClass()?.let { lightClass ->
    this[lightClass] = CreateKotlinCallableFromKotlinUsageRequest(
        callExpression, mutableSetOf(), receiverExpression, isAbstractClassOrInterface = true
    )
}

private fun LinkedHashMap<JvmClass, CreateMethodRequest>.registerCreateExtensionCallableFromUsage(
    callExpression: KtCallExpression, calleeExpression: KtSimpleNameExpression, receiverExpression: KtExpression?
) {
    val containerClassForExtension = calleeExpression.getContainerClass()
    containerClassForExtension?.let { jvmClass ->
        this[jvmClass] = CreateKotlinCallableFromKotlinUsageRequest(callExpression, mutableSetOf(), receiverExpression, isExtension = true)
    }
}