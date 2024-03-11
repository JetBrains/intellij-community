// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.lang.jvm.actions.EP_NAME
import com.intellij.lang.jvm.actions.groupActionsByType
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.KtStarTypeProjection
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.symbols.KtClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.CreateFromUsageUtil
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * This function uses [buildRequestsAndActions] below to prepare requests for creating Kotlin callables from usage in Kotlin
 * and create `IntentionAction`s for the requests.
 */
internal fun generateCreateMethodActions(element: KtElement): List<IntentionAction> {
    val callExpression = getTargetCallExpression(element) ?: return emptyList()
    val calleeExpression = callExpression.calleeExpression as? KtSimpleNameExpression ?: return emptyList()
    if (!calleeExpression.referenceNameOfElement()) return emptyList()
    return buildRequestsAndActions(callExpression)
}

private fun getTargetCallExpression(element: KtElement): KtCallExpression? {
    if (element.isPartOfImportDirectiveOrAnnotation()) return null
    val parent = element.parent
    return if (parent is KtCallExpression && parent.calleeExpression == element) parent else null
}

private fun KtSimpleNameExpression.referenceNameOfElement(): Boolean = getReferencedNameElementType() == KtTokens.IDENTIFIER

internal fun buildRequestsAndActions(callExpression: KtCallExpression): List<IntentionAction> {
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
        if (defaultClassForReceiverOrFile != null) {
            val shouldCreateCompanionClass = shouldCreateCompanionClass(calleeExpression)
            val modifiers = computeModifiers(calleeExpression, callExpression, shouldCreateCompanionClass)
            requests[defaultClassForReceiverOrFile] = CreateMethodFromKotlinUsageRequest(
                functionCall = callExpression,
                modifiers = modifiers,
                receiverExpression = receiverExpression,
                isExtension = false,
                isAbstractClassOrInterface = false,
                isForCompanion = shouldCreateCompanionClass
            )
        }
        // Register create-abstract/extension-callable-from-usage request.
        val abstractTypeOfContainer = calleeExpression.getAbstractTypeOfReceiver()
        val abstractContainerClass = abstractTypeOfContainer?.convertToClass()
        when {
            abstractContainerClass != null -> requests.registerCreateAbstractCallableFromUsage(
                abstractContainerClass, callExpression, receiverExpression
            )

            receiverExpression != null -> requests.registerCreateExtensionCallableFromUsage(
                callExpression, calleeExpression, receiverExpression
            )
        }
    }
    return requests
}

context (KtAnalysisSession)
fun shouldCreateCompanionClass(calleeExpression: KtSimpleNameExpression): Boolean {
    val receiverExpression = calleeExpression.getReceiverExpression()
    val receiverResolved =
        (receiverExpression as? KtNameReferenceExpression)?.mainReference?.resolveToSymbol() as? KtClassOrObjectSymbol
    return receiverResolved != null && receiverResolved.classKind != KtClassKind.OBJECT && receiverResolved.classKind != KtClassKind.COMPANION_OBJECT
}

// assume the map is linked, because we require order
val modifiers: Map<KtModifierKeywordToken, JvmModifier> = mapOf(
    KtTokens.PRIVATE_KEYWORD to JvmModifier.PRIVATE,
    KtTokens.INTERNAL_KEYWORD to JvmModifier.PRIVATE, // create private from internal
    KtTokens.PROTECTED_KEYWORD to JvmModifier.PROTECTED,
    KtTokens.PUBLIC_KEYWORD to JvmModifier.PUBLIC
)

context (KtAnalysisSession)
private fun computeModifiers(
    calleeExpression: KtSimpleNameExpression,
    callExpression: KtCallExpression,
    shouldCreateCompanionClass: Boolean
): List<JvmModifier> {
    if (shouldCreateCompanionClass) {
        return listOf(JvmModifier.PUBLIC, JvmModifier.STATIC) // methods in the companion class are typically public (and static in case of java)
    }
    val packageNameOfReceiver = calleeExpression.getReceiverOrContainerClassPackageName()
    if (packageNameOfReceiver != null && packageNameOfReceiver == callExpression.containingKtFile.packageFqName) {
        return emptyList()
    }
    val modifierOwner = callExpression.getNonStrictParentOfType<KtModifierListOwner>() ?: return emptyList()

    for (modifier in modifiers) {
        if (modifierOwner.hasModifier(modifier.key)) {
            return listOf(modifier.value)
        }
    }
    val modifier = CreateFromUsageUtil.computeVisibilityModifier(callExpression)
    if (modifier != null) {
        return listOf(modifiers[modifier]!!)
    }
    return emptyList()
}
/**
 * Returns the type of the class containing this [KtSimpleNameExpression] if the class is abstract. Otherwise, returns null.
 */
context (KtAnalysisSession)
private fun KtSimpleNameExpression.getAbstractTypeOfContainingClass(): KtType? {
    val containingClass = getStrictParentOfType<KtClassOrObject>() as? KtClass ?: return null
    if (containingClass is KtEnumEntry || containingClass.isAnnotation()) return null

    val classSymbol = containingClass.getSymbol() as? KtClassOrObjectSymbol ?: return null
    val classType = buildClassType(classSymbol) {
        for (typeParameter in containingClass.typeParameters) {
            argument(KtStarTypeProjection(token))
        }
    }
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

private fun MutableMap<JvmClass, CreateMethodRequest>.registerCreateAbstractCallableFromUsage(
    abstractContainerClass: KtClass, callExpression: KtCallExpression, receiverExpression: KtExpression?
) {
    val jvmClassWrapper = JvmClassWrapperForKtClass(abstractContainerClass)
    this[jvmClassWrapper] = CreateMethodFromKotlinUsageRequest(
        callExpression, setOf(), receiverExpression, isAbstractClassOrInterface = true, isExtension = false, isForCompanion = false
    )
}

private fun MutableMap<JvmClass, CreateMethodRequest>.registerCreateExtensionCallableFromUsage(
    callExpression: KtCallExpression, calleeExpression: KtSimpleNameExpression, receiverExpression: KtExpression?
) {
    val containerClassForExtension = calleeExpression.getNonStrictParentOfType<KtClassOrObject>() ?: calleeExpression.containingKtFile
    val jvmClassWrapper = JvmClassWrapperForKtClass(containerClassForExtension)
    this[jvmClassWrapper] = CreateMethodFromKotlinUsageRequest(callExpression, setOf(), receiverExpression, isExtension = true, isAbstractClassOrInterface = false, isForCompanion = false)
}