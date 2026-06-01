// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.lang.jvm.actions.EP_NAME
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.allSupertypes
import org.jetbrains.kotlin.analysis.api.components.directSupertypes
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.components.scopeContext
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.base.analysis.api.utils.buildClassTypeWithStarProjections
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateMethodFromKotlinUsageRequest.Companion.createMethodRequest
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.canRefactor
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.convertToClass
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.getClassOfExpressionType
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.getReceiverOrContainerClass
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.getReceiverOrContainerClassPackageName
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.hasAbstractDeclaration
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.hasAbstractModifier
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.isPartOfImportDirectiveOrAnnotation
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.CreateFromUsageUtil
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructorDelegationCall
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtSuperTypeList
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypeAndBranch
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypeAndBranches
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration
import org.jetbrains.kotlin.types.expressions.OperatorConventions

object K2CreateFunctionFromUsageBuilder {
    /**
     * This function uses [buildRequestsAndActions] below to prepare requests for creating Kotlin callables from usage in Kotlin
     * and create `IntentionAction`s for the requests.
     */
    internal fun generateCreateMethodActions(element: KtElement): List<IntentionAction> {
        if (element.isPartOfImportDirectiveOrAnnotation()) return emptyList()
        val parent = element.parent
        val callExpression = if (parent is KtCallExpression && parent.calleeExpression == element) parent else null
        if (callExpression != null) {
            val calleeExpression = callExpression.calleeExpression as? KtSimpleNameExpression ?: return emptyList()
            if (!calleeExpression.referenceNameOfElement()) return emptyList()
            return buildRequestsAndActions(callExpression)
        }

        val binaryExpression = element as? KtBinaryExpression ?: parent as? KtBinaryExpression ?: return emptyList()
        return buildRequestsAndActions(binaryExpression)
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

    internal fun buildRequestsAndActions(binaryExpression: KtBinaryExpression): List<IntentionAction> {
        val methodRequests = analyze(binaryExpression) { buildRequests(binaryExpression) }
        val extensions = EP_NAME.extensions
        return methodRequests.flatMap { (targetClass, request) ->
            extensions.flatMap { ext ->
                ext.createAddMethodActions(targetClass, request)
            }
        }
    }

    context(_: KaSession)
    private fun buildRequests(callExpression: KtCallExpression): List<Pair<JvmClass, CreateMethodRequest>> {
        val calleeExpression = callExpression.calleeExpression as? KtSimpleNameExpression ?: return emptyList()
        return buildRequests(
            usageElement = callExpression,
            calleeExpression = calleeExpression,
            referenceName = (callExpression.calleeExpression as? KtSimpleNameExpression)?.getReferencedName().orEmpty(),
            receiverExpression = calleeExpression.getReceiverExpression(),
            implicitReceivers = computeImplicitReceiverType(calleeExpression),
            operatorFunction = false,
        )
    }

    context(_: KaSession)
    private fun buildRequests(binaryExpression: KtBinaryExpression): List<Pair<JvmClass, CreateMethodRequest>> {
        val inOperation = binaryExpression.operationToken in OperatorConventions.IN_OPERATIONS

        val receiverExpr = if (inOperation) binaryExpression.right else binaryExpression.left
        if (receiverExpr == null) return emptyList()

        val token = binaryExpression.operationToken as KtToken
        val operationName = when (token) {
            KtTokens.IDENTIFIER -> binaryExpression.operationReference.getReferencedName()
            else -> OperatorConventions.getNameForOperationSymbol(token, false, true)?.asString()
        } ?: return emptyList()
        return buildRequests(
            usageElement = binaryExpression,
            calleeExpression = binaryExpression.operationReference,
            referenceName = operationName,
            receiverExpression = receiverExpr,
            implicitReceivers = emptyList(),
            operatorFunction = true,
        )
    }

    context(_: KaSession)
    private fun buildRequests(
        usageElement: KtElement,
        calleeExpression: KtSimpleNameExpression,
        referenceName: String,
        receiverExpression: KtExpression?,
        implicitReceivers: List<KaType>,
        operatorFunction: Boolean,
    ): List<Pair<JvmClass, CreateMethodRequest>> {
        val requests = mutableListOf<Pair<JvmClass, CreateMethodRequest>>()
        // Register default create-from-usage request.
        // TODO: Check whether this class or file can be edited (Use `canRefactor()`).
        val receiverClass = receiverExpression?.getClassOfExpressionType()
        val defaultContainers = when (receiverClass) {
            is PsiClass -> listOf(receiverClass)
            is KtClassOrObject -> listOf(receiverClass)
            else -> implicitReceivers.map { it.convertToClass() }.takeIf { it.isNotEmpty() } ?: listOf(PsiTreeUtil.getParentOfType(
                /* element = */ calleeExpression,
                /* aClass = */ KtClassOrObject::class.java,
                /* strict = */ false,
                /* ...stopAt = */ KtSuperTypeList::class.java, KtPrimaryConstructor::class.java, KtConstructorDelegationCall::class.java
            ))
        }

        defaultContainers.forEachIndexed { index, container ->
          val defaultClassForReceiverOrFile = calleeExpression.getReceiverOrContainerClass(container)
            if (defaultClassForReceiverOrFile != null) {
                val shouldCreateCompanionClass = shouldCreateCompanionClass(receiverExpression)
                val effectiveContainer = container ?: calleeExpression.containingFile
                val modifiers = computeModifiers(
                    effectiveContainer,
                    CreateFromUsageUtil.isTopLevelScriptContainer(effectiveContainer),
                    calleeExpression,
                    usageElement,
                    shouldCreateCompanionClass,
                    false,
                )
                requests.add(defaultClassForReceiverOrFile to createMethodRequest(
                    functionCall = usageElement,
                    modifiers = modifiers,
                    referenceName = referenceName,
                    receiverExpression = receiverExpression,
                    receiverType = implicitReceivers.getOrNull(index).takeUnless { receiverClass is KtClassOrObject || receiverClass is PsiClass },
                    isExtension = false,
                    isAbstractClassOrInterface = false,
                    isForCompanion = shouldCreateCompanionClass,
                    operatorFunction = operatorFunction,
                    targetContainerClass = container as? KtClassOrObject,
                ))
            }
        }
        // Register create-abstract/extension-callable-from-usage request.
        val abstractTypeOfContainer = calleeExpression.getAbstractTypeOfReceiver(receiverExpression)
        val abstractContainerClass = abstractTypeOfContainer?.convertToClass()
        if (abstractContainerClass != null) {
            val jvmClass = abstractContainerClass.toLightClass()
            if (jvmClass != null) {
                requests.add(jvmClass to createMethodRequest(
                    functionCall = usageElement,
                    modifiers = emptySet(),
                    referenceName = referenceName,
                    receiverExpression = receiverExpression,
                    receiverType = null,
                    isAbstractClassOrInterface = true,
                    isExtension = false,
                    isForCompanion = false,
                    operatorFunction = operatorFunction,
                    targetContainerClass = abstractContainerClass,
                ))
            }
        }
        if (receiverExpression != null || implicitReceivers.isNotEmpty()) {
            val explicitReceiverType = receiverExpression?.expressionType
            val implicitReceiverType = implicitReceivers.firstOrNull()
            val containerClassForExtension: KtElement =
                implicitReceiverType?.convertToClass() ?: calleeExpression.getNonStrictParentOfType<KtClassOrObject>()
                ?: calleeExpression.containingKtFile
            val jvmClassWrapper = JvmClassWrapperForKtClass(containerClassForExtension)
            val shouldCreateCompanionClass = shouldCreateCompanionClass(receiverExpression)
            val containerIsScript = CreateFromUsageUtil.isTopLevelScriptContainer(containerClassForExtension)
            val modifiers = computeModifiers(
                container = defaultContainers.firstOrNull() ?:calleeExpression.containingFile,
                containerIsScript = containerIsScript,
                calleeExpression = calleeExpression,
                callExpression = usageElement,
                shouldCreateCompanionClass = shouldCreateCompanionClass,
                isExtension = true,
            )
            val request = createMethodRequest(
                functionCall = usageElement,
                modifiers = modifiers,
                referenceName = referenceName,
                receiverExpression = receiverExpression,
                receiverType = explicitReceiverType ?: implicitReceiverType,
                isExtension = true,
                isAbstractClassOrInterface = false,
                isForCompanion = shouldCreateCompanionClass,
                operatorFunction = operatorFunction,
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

    context(_: KaSession)
    private fun shouldCreateCompanionClass(receiverExpression: KtExpression?): Boolean {
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

    context(_: KaSession)
    private fun computeModifiers(
        container: PsiElement,
        containerIsScript: Boolean,
        calleeExpression: KtSimpleNameExpression,
        callExpression: KtElement,
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
            containerIsScript = containerIsScript,
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

    context(_: KaSession)
    private fun samePackage(
        calleeExpression: KtSimpleNameExpression,
        callExpression: KtElement
    ): Boolean {
        val packageNameOfReceiver = calleeExpression.getReceiverOrContainerClassPackageName()
        val samePackage = packageNameOfReceiver != null && packageNameOfReceiver == callExpression.containingKtFile.packageFqName
        return samePackage
    }

    /**
     * Returns the type of the class containing this [KtSimpleNameExpression] if the class is abstract. Otherwise, returns null.
     */
    context(_: KaSession)
    private fun KtSimpleNameExpression.getAbstractTypeOfContainingClass(): KaType? {
        val containingClass = PsiTreeUtil.getParentOfType(
            /* element = */ this,
            /* aClass = */ KtClassOrObject::class.java,
            /* strict = */ false,
            /* ...stopAt = */ KtSuperTypeList::class.java, KtPrimaryConstructor::class.java, KtConstructorDelegationCall::class.java, KtObjectDeclaration::class.java
        ) ?: return null
        if (containingClass is KtEnumEntry || containingClass.isAnnotation()) return null

        val classSymbol = containingClass.symbol as? KaClassSymbol ?: return null
        val classType = buildClassTypeWithStarProjections(classSymbol)
        if (containingClass.modifierList.hasAbstractModifier() || classSymbol.classKind == KaClassKind.INTERFACE) return classType

        // KaType.getAbstractSuperType() does not guarantee it's the closest abstract super type. We can implement it as a
        // breadth-first search, but it can cost a lot in terms of the memory usage.
        return classType.getAbstractSuperType()
    }

    context(_: KaSession)
    private fun KaType.getAbstractSuperType(): KaType? {
        fun Sequence<KaType>.firstAbstractEditableType() = firstOrNull { it.hasAbstractDeclaration() && it.canRefactor() }
        return directSupertypes.firstAbstractEditableType() ?: allSupertypes.firstAbstractEditableType()
    }

    /**
     * Returns class or superclass of the express's type if the class or the super class is abstract. Otherwise, returns null.
     */
    context(_: KaSession)
    private fun KtExpression.getTypeOfAbstractSuperClass(): KaType? {
        val type = expressionType ?: return null
        if (type.hasAbstractDeclaration()) return type
        return type.allSupertypes.firstOrNull { it.hasAbstractDeclaration() }
    }

    /**
     * Returns the receiver's type if it is abstract, or it has an abstract superclass. Otherwise, returns null.
     */
    context(_: KaSession)
    private fun KtSimpleNameExpression.getAbstractTypeOfReceiver(receiverExpression: KtExpression? = getReceiverExpression()): KaType? {
        // If no explicit receiver exists, the containing class can be an implicit receiver.
        val receiver = receiverExpression ?: return getAbstractTypeOfContainingClass()
        return receiver.getTypeOfAbstractSuperClass()
    }

    context(_: KaSession)
    fun computeImplicitReceiverType(calleeExpression: KtSimpleNameExpression): List<KaType> {
        return calleeExpression.containingKtFile.scopeContext(calleeExpression).implicitReceivers.mapNotNull { implicitReceiver ->
            val callable = (calleeExpression.getParentOfTypeAndBranch<KtFunction> { bodyExpression }
                ?: calleeExpression.getParentOfTypeAndBranches<KtProperty> { listOf(getter, setter) })
                ?: return@mapNotNull null
            if (callable !is KtFunctionLiteral && callable.receiverTypeReference == null) return@mapNotNull null

            val type = implicitReceiver.type
            if (type is KaTypeParameterType) {
                type.directSupertypes.firstOrNull()
            }
            else type
        }
    }
}
