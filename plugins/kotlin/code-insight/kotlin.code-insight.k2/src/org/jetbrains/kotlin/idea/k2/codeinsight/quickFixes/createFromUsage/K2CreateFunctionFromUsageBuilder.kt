// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.lang.jvm.actions.EP_NAME
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageBuilder.buildRequestsAndActions
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.canRefactor
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.convertToClass
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.getReceiverOrContainerClass
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.getReceiverOrContainerClassPackageName
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.getReceiverOrContainerPsiElement
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.hasAbstractDeclaration
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.hasAbstractModifier
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.isPartOfImportDirectiveOrAnnotation
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.CreateFromUsageUtil
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*

object K2CreateFunctionFromUsageBuilder {
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
        val methodRequests = analyze(callExpression) { buildRequests(callExpression) }
        val extensions = EP_NAME.extensions
        return methodRequests.flatMap { (targetClass, request) ->
            extensions.flatMap { ext ->
                ext.createAddMethodActions(targetClass, request)
            }
        }
    }

    context (KaSession)
    private fun buildRequests(callExpression: KtCallExpression): List<Pair<JvmClass, CreateMethodRequest>> {
        val calleeExpression = callExpression.calleeExpression as? KtSimpleNameExpression ?: return emptyList()
        val requests = mutableListOf<Pair<JvmClass, CreateMethodRequest>>()
        val receiverExpression = calleeExpression.getReceiverExpression()
        // Register default create-from-usage request.
        // TODO: Check whether this class or file can be edited (Use `canRefactor()`).
        val defaultContainerPsi = calleeExpression.getReceiverOrContainerPsiElement()
        val defaultClassForReceiverOrFile = calleeExpression.getReceiverOrContainerClass(defaultContainerPsi)
        if (defaultClassForReceiverOrFile != null) {
            val shouldCreateCompanionClass = shouldCreateCompanionClass(calleeExpression)
            val modifiers = computeModifiers(
                defaultContainerPsi?:calleeExpression.containingFile,
                calleeExpression,
                callExpression,
                shouldCreateCompanionClass, false
            )
            requests.add(defaultClassForReceiverOrFile to CreateMethodFromKotlinUsageRequest(
                functionCall = callExpression,
                modifiers = modifiers,
                receiverExpression = receiverExpression,
                receiverType = computeImplicitReceiverType(calleeExpression),
                isExtension = false,
                isAbstractClassOrInterface = false,
                isForCompanion = shouldCreateCompanionClass
            ))
        }
        // Register create-abstract/extension-callable-from-usage request.
        val abstractTypeOfContainer = calleeExpression.getAbstractTypeOfReceiver()
        val abstractContainerClass = abstractTypeOfContainer?.convertToClass()
        if (abstractContainerClass != null) {
            val jvmClass = abstractContainerClass.toLightClass()
            if (jvmClass != null) {
                requests.add(jvmClass to CreateMethodFromKotlinUsageRequest(
                    callExpression,
                    setOf(),
                    receiverExpression,
                    receiverType = null,
                    isAbstractClassOrInterface = true,
                    isExtension = false,
                    isForCompanion = false,
                ))
            }
        }
        if (receiverExpression != null || computeImplicitReceiverClass(calleeExpression) != null) {
            val explicitReceiverType = receiverExpression?.expressionType
            val implicitReceiverType = computeImplicitReceiverType(calleeExpression)
            val containerClassForExtension: KtElement =
                implicitReceiverType?.convertToClass() ?: calleeExpression.getNonStrictParentOfType<KtClassOrObject>()
                ?: calleeExpression.containingKtFile
            val jvmClassWrapper = JvmClassWrapperForKtClass(containerClassForExtension)
            val shouldCreateCompanionClass = shouldCreateCompanionClass(calleeExpression)
            val modifiers = computeModifiers(defaultContainerPsi?:calleeExpression.containingFile, calleeExpression, callExpression, shouldCreateCompanionClass, true)
            val request = CreateMethodFromKotlinUsageRequest(
                callExpression,
                modifiers,
                receiverExpression,
                receiverType = explicitReceiverType ?: implicitReceiverType,
                isExtension = true,
                isAbstractClassOrInterface = false,
                isForCompanion = shouldCreateCompanionClass,
            )
            if (explicitReceiverType !is KaErrorType && !hasExtensionFunction(containerClassForExtension, request.methodName)) {
                requests.add(jvmClassWrapper to request)
            }
        }
        return requests
    }

    private fun hasExtensionFunction(containerClassForExtension: KtElement, name: @NlsSafe String): Boolean {
        return containerClassForExtension.containingFile.children.find { it.isExtensionDeclaration() && it is PsiNamedElement && it.name == name} != null
    }

    context (KaSession)
    private fun shouldCreateCompanionClass(calleeExpression: KtSimpleNameExpression): Boolean {
        val receiverExpression = calleeExpression.getReceiverExpression()
        val receiverResolved =
            (receiverExpression as? KtNameReferenceExpression)?.mainReference?.resolveToSymbol() as? KaClassSymbol
        return receiverResolved != null && receiverResolved.classKind != KaClassKind.OBJECT && receiverResolved.classKind != KaClassKind.COMPANION_OBJECT
    }

    // assume the map is linked, because we require order
    private val allModifiers: Map<KtModifierKeywordToken, JvmModifier> = mapOf(
        KtTokens.PRIVATE_KEYWORD to JvmModifier.PRIVATE,
        KtTokens.INTERNAL_KEYWORD to JvmModifier.PRIVATE, // create private from internal
        KtTokens.PROTECTED_KEYWORD to JvmModifier.PROTECTED,
        KtTokens.PUBLIC_KEYWORD to JvmModifier.PUBLIC
    )

    context (KaSession)
    private fun computeModifiers(
        container: PsiElement,
        calleeExpression: KtSimpleNameExpression,
        callExpression: KtCallExpression,
        shouldCreateCompanionClass: Boolean,
        isExtension: Boolean
    ): List<JvmModifier> {
        if (shouldCreateCompanionClass) {
            // methods in the companion class are typically public (and static in case of java)
            return if (samePackage(calleeExpression, callExpression)) if (isExtension) listOf(JvmModifier.PRIVATE, JvmModifier.STATIC) else listOf(JvmModifier.STATIC) else listOf(JvmModifier.PUBLIC, JvmModifier.STATIC)
        }
        val modifier = CreateFromUsageUtil.patchVisibilityForInlineFunction(callExpression)
        if (modifier != null) {
            return listOf(allModifiers[modifier]!!)
        }
        val modifierOwner = callExpression.getNonStrictParentOfType<KtModifierListOwner>()
        val samePackage = samePackage(calleeExpression, callExpression)
        if (modifierOwner != null && !samePackage) {
            for (entry in allModifiers) {
                if (modifierOwner.hasModifier(entry.key)) {
                    return listOf(entry.value)
                }
            }
        }

        val jvmModifier = CreateFromUsageUtil.computeDefaultVisibilityAsJvmModifier(
            container,
            isAbstract = false,
            isExtension = isExtension,
            isConstructor = false,
            originalElement = callExpression
        )
        if (jvmModifier != null) {
            return listOf(jvmModifier)
        }

        return listOf(JvmModifier.PUBLIC)
    }

    context (KaSession)
    private fun samePackage(
        calleeExpression: KtSimpleNameExpression,
        callExpression: KtCallExpression
    ): Boolean {
        val packageNameOfReceiver = calleeExpression.getReceiverOrContainerClassPackageName()
        val samePackage = packageNameOfReceiver != null && packageNameOfReceiver == callExpression.containingKtFile.packageFqName
        return samePackage
    }

    /**
     * Returns the type of the class containing this [KtSimpleNameExpression] if the class is abstract. Otherwise, returns null.
     */
    context (KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun KtSimpleNameExpression.getAbstractTypeOfContainingClass(): KaType? {
        val containingClass = PsiTreeUtil.getParentOfType(
            /* element = */ this,
            /* aClass = */ KtClassOrObject::class.java,
            /* strict = */ false,
            /* ...stopAt = */ KtSuperTypeList::class.java, KtPrimaryConstructor::class.java, KtConstructorDelegationCall::class.java, KtObjectDeclaration::class.java
        ) ?: return null
        if (containingClass is KtEnumEntry || containingClass.isAnnotation()) return null

        val classSymbol = containingClass.symbol as? KaClassSymbol ?: return null
        val classType = buildClassType(classSymbol) {
            for (typeParameter in containingClass.typeParameters) {
                argument(buildStarTypeProjection())
            }
        }
        if (containingClass.modifierList.hasAbstractModifier() || classSymbol.classKind == KaClassKind.INTERFACE) return classType

        // KaType.getAbstractSuperType() does not guarantee it's the closest abstract super type. We can implement it as a
        // breadth-first search, but it can cost a lot in terms of the memory usage.
        return classType.getAbstractSuperType()
    }

    context (KaSession)
    private fun KaType.getAbstractSuperType(): KaType? {
        fun Sequence<KaType>.firstAbstractEditableType() = firstOrNull { it.hasAbstractDeclaration() && it.canRefactor() }
        return directSupertypes.firstAbstractEditableType() ?: allSupertypes.firstAbstractEditableType()
    }

    /**
     * Returns class or superclass of the express's type if the class or the super class is abstract. Otherwise, returns null.
     */
    context (KaSession)
    private fun KtExpression.getTypeOfAbstractSuperClass(): KaType? {
        val type = expressionType ?: return null
        if (type.hasAbstractDeclaration()) return type
        return type.allSupertypes.firstOrNull { it.hasAbstractDeclaration() }
    }

    /**
     * Returns the receiver's type if it is abstract, or it has an abstract superclass. Otherwise, returns null.
     */
    context (KaSession)
    private fun KtSimpleNameExpression.getAbstractTypeOfReceiver(): KaType? {
        // If no explicit receiver exists, the containing class can be an implicit receiver.
        val receiver = getReceiverExpression() ?: return getAbstractTypeOfContainingClass()
        return receiver.getTypeOfAbstractSuperClass()
    }

    context (KaSession)
    fun computeImplicitReceiverClass(calleeExpression: KtSimpleNameExpression): KtClass? {
        return computeImplicitReceiverType(calleeExpression)?.convertToClass()
    }
    context (KaSession)
    private fun computeImplicitReceiverType(calleeExpression: KtSimpleNameExpression): KaType? {
        val implicitReceiver = calleeExpression.containingKtFile.scopeContext(calleeExpression).implicitReceivers.firstOrNull()
        if (implicitReceiver != null) {
            val callable = (calleeExpression.getParentOfTypeAndBranch<KtFunction> { bodyExpression }
                ?: calleeExpression.getParentOfTypeAndBranches<KtProperty> { listOf(getter, setter) })
                ?: return null
            if (callable !is KtFunctionLiteral && callable.receiverTypeReference == null) return null

            var type: KaType? = implicitReceiver.type
            if (type is KaTypeParameterType) {
                type = type.directSupertypes.firstOrNull()
            }
            return type
        }
        return null
    }
}
