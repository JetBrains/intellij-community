// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.lang.jvm.actions.EP_NAME
import com.intellij.lang.jvm.actions.groupActionsByType
import com.intellij.psi.PsiReference
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.symbols.KtClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

fun generateCreateKotlinCallableActions(ref: PsiReference): List<IntentionAction> {
    val callExpression = getCallExpressionToCreate(ref) ?: return emptyList()
    val calleeExpression = callExpression.calleeExpression as? KtSimpleNameExpression ?: return emptyList()
    if (calleeExpression.getReferencedNameElementType() != KtTokens.IDENTIFIER) return emptyList()
    return buildRequestsAndActions(callExpression)
}

private fun getCallExpressionToCreate(ref: PsiReference): KtCallExpression? {
    val element = ref.element as? KtElement ?: return null
    if (element.isPartOfImportDirectiveOrAnnotation()) return null
    val parent = element.parent
    return if (parent is KtCallExpression && parent.calleeExpression == element) parent else null
}

private fun buildRequestsAndActions(callExpression: KtCallExpression): List<IntentionAction> {
    val methodRequests = buildRequests(callExpression)
    val extensions = EP_NAME.extensions
    return methodRequests.flatMap { (clazz, request) ->
        extensions.flatMap { ext ->
            ext.createAddMethodActions(clazz, request)
        }
    }.groupActionsByType(KotlinLanguage.INSTANCE)
}

fun buildRequests(callExpression: KtCallExpression): Map<JvmClass, CreateMethodRequest> {
    val requests = LinkedHashMap<JvmClass, CreateMethodRequest>()
    val calleeExpression = callExpression.calleeExpression as? KtSimpleNameExpression ?: return requests
    analyze(callExpression) {
        // TODO: Check whether this class or file can be edited (Use `canRefactor()`).
        val receiverExpression = calleeExpression.getReceiverExpression()
        val defaultClassForReceiverOrFile = calleeExpression.getReceiverOrContainerClass()
        defaultClassForReceiverOrFile?.let {
            requests[it] = CreateKotlinCallableFromKotlinUsageRequest(callExpression, mutableSetOf(), receiverExpression)
        }

        val abstractTypeOfContainer = calleeExpression.getAbstractTypeOfReceiver()
        val abstractContainerClass = abstractTypeOfContainer?.convertToClass()
        when {
            abstractContainerClass != null -> abstractContainerClass.toLightClass()?.let { lightClass ->
                requests[lightClass] = CreateKotlinCallableFromKotlinUsageRequest(
                    callExpression, mutableSetOf(), receiverExpression, isAbstractClassOrInterface = true
                )
            }

            receiverExpression != null -> {
                val containerClassForExtension = calleeExpression.getContainerClass()
                containerClassForExtension?.let { jvmClass ->
                    requests[jvmClass] =
                        CreateKotlinCallableFromKotlinUsageRequest(callExpression, mutableSetOf(), receiverExpression, isExtension = true)
                }
            }

            else -> {}
        }
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