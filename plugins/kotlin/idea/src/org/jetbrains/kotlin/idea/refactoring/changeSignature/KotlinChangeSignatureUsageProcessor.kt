// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.changeSignature

import com.intellij.codeInsight.NullableNotNullManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.changeSignature.*
import com.intellij.refactoring.rename.ResolveSnapshotProvider
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.refactoring.util.TextOccurrencesUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.namedUnwrappedElement
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.project.forcedModuleInfo
import org.jetbrains.kotlin.idea.caches.project.getModuleInfo
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.util.getJavaMethodDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.util.getJavaOrKotlinMemberDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.util.javaResolutionFacade
import org.jetbrains.kotlin.idea.core.compareDescriptors
import org.jetbrains.kotlin.idea.refactoring.changeSignature.usages.*
import org.jetbrains.kotlin.idea.refactoring.createJavaMethod
import org.jetbrains.kotlin.idea.refactoring.isTrueJavaMethod
import org.jetbrains.kotlin.idea.references.KtArrayAccessReference
import org.jetbrains.kotlin.idea.references.KtInvokeFunctionReference
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.search.codeUsageScopeRestrictedToKotlinSources
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinReferencesSearchOptions
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinReferencesSearchParameters
import org.jetbrains.kotlin.idea.search.usagesSearch.processDelegationCallConstructorUsages
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.getReceiverTargetDescriptor
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.load.java.descriptors.JavaClassDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypeAndBranch
import org.jetbrains.kotlin.psi.psiUtil.getValueParameters
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.VariableAsFunctionResolvedCall
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.receivers.ExtensionReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ImplicitReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class KotlinChangeSignatureUsageProcessor : ChangeSignatureUsageProcessor {

    // This is special 'PsiElement' whose purpose is to wrap JetMethodDescriptor so that it can be kept in the usage list
    private class OriginalJavaMethodDescriptorWrapper(element: PsiElement) : UsageInfo(element) {
        var originalJavaMethodDescriptor: KotlinMethodDescriptor? = null
    }

    private class DummyKotlinChangeInfo(
        method: PsiElement,
        methodDescriptor: KotlinMethodDescriptor
    ) : KotlinChangeInfo(
        methodDescriptor = methodDescriptor,
        name = "",
        newReturnTypeInfo = KotlinTypeInfo(true),
        newVisibility = DescriptorVisibilities.DEFAULT_VISIBILITY,
        parameterInfos = emptyList(),
        receiver = null,
        context = method
    )

    // It's here to prevent O(usage_count^2) performance
    private var initializedOriginalDescriptor: Boolean = false

    override fun findUsages(info: ChangeInfo): Array<UsageInfo> {
        if (!canHandle(info)) return UsageInfo.EMPTY_ARRAY

        initializedOriginalDescriptor = false

        val result = hashSetOf<UsageInfo>()

        result.add(OriginalJavaMethodDescriptorWrapper(info.method))

        val kotlinChangeInfo = info.asKotlinChangeInfo
        if (kotlinChangeInfo != null) {
            findAllMethodUsages(kotlinChangeInfo, result)
        } else {
            findSAMUsages(info, result)
            //findConstructorDelegationUsages(info, result)
            findKotlinOverrides(info, result)
            if (info is JavaChangeInfo) {
                findKotlinCallers(info, result)
            }
        }

        return result.toTypedArray()
    }

    private fun canHandle(changeInfo: ChangeInfo) = changeInfo.asKotlinChangeInfo is KotlinChangeInfo || changeInfo is JavaChangeInfo

    private fun findAllMethodUsages(changeInfo: KotlinChangeInfo, result: MutableSet<UsageInfo>) {
        loop@ for (functionUsageInfo in changeInfo.getAffectedCallables()) {
            when (functionUsageInfo) {
                is KotlinCallableDefinitionUsage<*> -> findOneMethodUsages(functionUsageInfo, changeInfo, result)
                is KotlinCallerUsage -> findCallerUsages(functionUsageInfo, changeInfo, result)
                else -> {
                    result.add(functionUsageInfo)

                    val callee = functionUsageInfo.element ?: continue@loop

                    val propagationTarget = functionUsageInfo is CallerUsageInfo ||
                            (functionUsageInfo is OverriderUsageInfo && !functionUsageInfo.isOriginalOverrider)

                    for (reference in ReferencesSearch.search(callee, callee.codeUsageScopeRestrictedToKotlinSources())) {
                        val callElement = reference.element.getParentOfTypeAndBranch<KtCallElement> { calleeExpression } ?: continue
                        val usage = if (propagationTarget) {
                            KotlinCallerCallUsage(callElement)
                        } else {
                            KotlinFunctionCallUsage(callElement, changeInfo.methodDescriptor.originalPrimaryCallable)
                        }

                        result.add(usage)
                    }
                }
            }
        }
    }

    private fun findCallerUsages(callerUsage: KotlinCallerUsage, changeInfo: KotlinChangeInfo, result: MutableSet<UsageInfo>) {
        result.add(callerUsage)

        val element = callerUsage.element ?: return

        for (ref in ReferencesSearch.search(element, element.useScope)) {
            val callElement = ref.element.getParentOfTypeAndBranch<KtCallElement> { calleeExpression } ?: continue
            result.add(KotlinCallerCallUsage(callElement))
        }

        val body = element.getDeclarationBody() ?: return
        val callerDescriptor = element.resolveToDescriptorIfAny() ?: return
        val context = body.analyze()
        val newParameterNames = changeInfo.getNonReceiverParameters().mapTo(hashSetOf()) { it.name }
        body.accept(
            object : KtTreeVisitorVoid() {
                override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                    val currentName = expression.getReferencedName()
                    if (currentName !in newParameterNames) return

                    val resolvedCall = expression.getResolvedCall(context) ?: return

                    if (resolvedCall.explicitReceiverKind != ExplicitReceiverKind.NO_EXPLICIT_RECEIVER) return

                    val resultingDescriptor = resolvedCall.resultingDescriptor
                    if (resultingDescriptor !is VariableDescriptor) return

                    // Do not report usages of duplicated parameter
                    if (resultingDescriptor is ValueParameterDescriptor && resultingDescriptor.containingDeclaration === callerDescriptor) return

                    val callElement = resolvedCall.call.callElement

                    var receiver = resolvedCall.extensionReceiver
                    if (receiver !is ImplicitReceiver) {
                        receiver = resolvedCall.dispatchReceiver
                    }

                    if (receiver is ImplicitReceiver) {
                        result.add(KotlinImplicitThisUsage(callElement, receiver.declarationDescriptor))
                    } else if (receiver == null) {
                        result.add(
                            object : UnresolvableCollisionUsageInfo(callElement, null) {
                                override fun getDescription(): String {
                                    val signature = IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS.render(callerDescriptor)
                                    return KotlinBundle.message(
                                        "text.there.is.already.a.variable.0.in.1.it.will.conflict.with.the.new.parameter",
                                        currentName,
                                        signature
                                    )
                                }
                            }
                        )
                    }
                }
            })
    }

    private fun findReferences(functionPsi: PsiElement): Set<PsiReference> {
        val result = LinkedHashSet<PsiReference>()
        val searchScope = functionPsi.useScope
        val options = KotlinReferencesSearchOptions(
            acceptCallableOverrides = true,
            acceptOverloads = false,
            acceptExtensionsOfDeclarationClass = false,
            acceptCompanionObjectMembers = false
        )

        val parameters = KotlinReferencesSearchParameters(functionPsi, searchScope, false, null, options)
        result.addAll(ReferencesSearch.search(parameters).findAll())
        if (functionPsi is KtProperty || functionPsi is KtParameter) {
            functionPsi.toLightMethods().flatMapTo(result) { MethodReferencesSearch.search(it, searchScope, true).findAll() }
        }

        return result
    }

    private fun findOneMethodUsages(
        functionUsageInfo: KotlinCallableDefinitionUsage<*>,
        changeInfo: KotlinChangeInfo,
        result: MutableSet<UsageInfo>
    ) {
        if (functionUsageInfo.isInherited) {
            result.add(functionUsageInfo)
        }

        val functionPsi = functionUsageInfo.element ?: return
        for (reference in findReferences(functionPsi)) {
            val element = reference.element
            when {
                reference is KtInvokeFunctionReference || reference is KtArrayAccessReference -> {
                    result.add(KotlinByConventionCallUsage(element as KtExpression, functionUsageInfo))
                }

                element is KtReferenceExpression -> {
                    val parent = element.parent
                    when {
                        parent is KtCallExpression -> result.add(KotlinFunctionCallUsage(parent, functionUsageInfo))

                        parent is KtUserType && parent.parent is KtTypeReference ->
                            parent.parent.parent.safeAs<KtConstructorCalleeExpression>()
                                ?.parent?.safeAs<KtCallElement>()
                                ?.let {
                                    result.add(KotlinFunctionCallUsage(it, functionUsageInfo))
                                }

                        element is KtSimpleNameExpression && (functionPsi is KtProperty || functionPsi is KtParameter) ->
                            result.add(KotlinPropertyCallUsage(element))
                    }
                }
            }
        }

        val searchScope = PsiSearchHelper.getInstance(functionPsi.project).getUseScope(functionPsi)

        changeInfo.oldName?.let { oldName ->
            TextOccurrencesUtil.findNonCodeUsages(
                functionPsi,
                searchScope,
                oldName,
                /* searchInStringsAndComments = */true,
                /* searchInNonJavaFiles = */true,
                changeInfo.newName,
                result
            )
        }

        val oldParameters = (functionPsi as KtNamedDeclaration).getValueParameters()

        val newReceiverInfo = changeInfo.receiverParameterInfo

        val isDataClass = functionPsi is KtPrimaryConstructor && (functionPsi.getContainingClassOrObject() as? KtClass)?.isData() ?: false

        for ((i, parameterInfo) in changeInfo.newParameters.withIndex()) {
            if (parameterInfo.oldIndex >= 0 && parameterInfo.oldIndex < oldParameters.size) {
                val oldParam = oldParameters[parameterInfo.oldIndex]
                val oldParamName = oldParam.name

                if (parameterInfo == newReceiverInfo || (oldParamName != null && oldParamName != parameterInfo.name) || isDataClass && i != parameterInfo.oldIndex) {
                    for (reference in ReferencesSearch.search(oldParam, oldParam.useScope)) {
                        val element = reference.element

                        if (isDataClass &&
                            element is KtSimpleNameExpression &&
                            (element.parent as? KtCallExpression)?.calleeExpression == element &&
                            element.getReferencedName() != parameterInfo.name &&
                            OperatorNameConventions.COMPONENT_REGEX.matches(element.getReferencedName())
                        ) {
                            result.add(KotlinDataClassComponentUsage(element, "component${i + 1}"))
                        }
                        // Usages in named arguments of the calls usage will be changed when the function call is changed
                        else if ((element is KtSimpleNameExpression || element is KDocName) && element.parent !is KtValueArgumentName) {
                            result.add(KotlinParameterUsage(element as KtElement, parameterInfo, functionUsageInfo))
                        }
                    }
                }
            }
        }

        if (isDataClass && !changeInfo.hasAppendedParametersOnly()) {
            (functionPsi as KtPrimaryConstructor).valueParameters.firstOrNull()?.let {
                ReferencesSearch.search(it).mapNotNullTo(result) { reference ->
                    val destructuringEntry = reference.element as? KtDestructuringDeclarationEntry ?: return@mapNotNullTo null
                    KotlinComponentUsageInDestructuring(destructuringEntry)
                }
            }
        }

        if (functionPsi is KtFunction && newReceiverInfo != changeInfo.methodDescriptor.receiver) {
            findOriginalReceiversUsages(functionUsageInfo, result, changeInfo)
        }

        if (functionPsi is KtClass && functionPsi.isEnum()) {
            for (declaration in functionPsi.declarations) {
                if (declaration is KtEnumEntry && declaration.superTypeListEntries.isEmpty()) {
                    result.add(KotlinEnumEntryWithoutSuperCallUsage(declaration))
                }
            }
        }

        functionPsi.processDelegationCallConstructorUsages(functionPsi.useScope) {
            when (it) {
                is KtConstructorDelegationCall -> result.add(KotlinConstructorDelegationCallUsage(it, changeInfo))
                is KtSuperTypeCallEntry -> result.add(KotlinFunctionCallUsage(it, functionUsageInfo))
            }
            true
        }
    }

    private fun processInternalReferences(functionUsageInfo: KotlinCallableDefinitionUsage<*>, visitor: KtTreeVisitor<BindingContext>) {
        val ktFunction = functionUsageInfo.declaration as KtFunction

        val body = ktFunction.bodyExpression
        body?.accept(visitor, body.analyze(BodyResolveMode.FULL))

        for (parameter in ktFunction.valueParameters) {
            val defaultValue = parameter.defaultValue
            defaultValue?.accept(visitor, defaultValue.analyze(BodyResolveMode.FULL))
        }
    }

    private fun findOriginalReceiversUsages(
        functionUsageInfo: KotlinCallableDefinitionUsage<*>,
        result: MutableSet<UsageInfo>,
        changeInfo: KotlinChangeInfo
    ) {
        val originalReceiverInfo = changeInfo.methodDescriptor.receiver
        val callableDescriptor = functionUsageInfo.originalCallableDescriptor
        processInternalReferences(
            functionUsageInfo,
            object : KtTreeVisitor<BindingContext>() {
                private fun processExplicitThis(
                    expression: KtSimpleNameExpression,
                    receiverDescriptor: ReceiverParameterDescriptor
                ) {
                    if (originalReceiverInfo != null && !changeInfo.hasParameter(originalReceiverInfo)) return
                    if (expression.parent !is KtThisExpression) return

                    if (receiverDescriptor === callableDescriptor.extensionReceiverParameter) {
                        assert(originalReceiverInfo != null) { "No original receiver info provided: " + functionUsageInfo.declaration.text }
                        result.add(KotlinParameterUsage(expression, originalReceiverInfo!!, functionUsageInfo))
                    } else {
                        if (receiverDescriptor.value is ExtensionReceiver) return
                        val targetDescriptor = receiverDescriptor.type.constructor.declarationDescriptor
                        assert(targetDescriptor != null) { "Receiver type has no descriptor: " + functionUsageInfo.declaration.text }
                        if (DescriptorUtils.isAnonymousObject(targetDescriptor!!)) return
                        result.add(KotlinNonQualifiedOuterThisUsage(expression.parent as KtThisExpression, targetDescriptor))
                    }
                }

                private fun processImplicitThis(
                    callElement: KtElement,
                    implicitReceiver: ImplicitReceiver
                ) {
                    val targetDescriptor = implicitReceiver.declarationDescriptor
                    if (compareDescriptors(callElement.project, targetDescriptor, callableDescriptor)) {
                        assert(originalReceiverInfo != null) { "No original receiver info provided: " + functionUsageInfo.declaration.text }
                        if (originalReceiverInfo in changeInfo.getNonReceiverParameters()) {
                            result.add(KotlinImplicitThisToParameterUsage(callElement, originalReceiverInfo!!, functionUsageInfo))
                        }
                    } else {
                        result.add(KotlinImplicitThisUsage(callElement, targetDescriptor))
                    }
                }

                private fun processThisCall(
                    expression: KtSimpleNameExpression,
                    context: BindingContext,
                    extensionReceiver: ReceiverValue?,
                    dispatchReceiver: ReceiverValue?
                ) {
                    val thisExpression = expression.parent as? KtThisExpression ?: return
                    val thisCallExpression = thisExpression.parent as? KtCallExpression ?: return
                    val usageInfo = when (callableDescriptor) {
                        dispatchReceiver.getReceiverTargetDescriptor(context) -> {
                            val extensionReceiverTargetDescriptor = extensionReceiver.getReceiverTargetDescriptor(context)
                            if (extensionReceiverTargetDescriptor != null) {
                                KotlinNonQualifiedOuterThisCallUsage(
                                    thisCallExpression, originalReceiverInfo!!, functionUsageInfo, extensionReceiverTargetDescriptor
                                )
                            } else {
                                KotlinParameterUsage(expression, originalReceiverInfo!!, functionUsageInfo)
                            }
                        }
                        extensionReceiver.getReceiverTargetDescriptor(context) -> {
                            KotlinParameterUsage(expression, originalReceiverInfo!!, functionUsageInfo)
                        }
                        else -> null
                    }
                    result.addIfNotNull(usageInfo)
                }

                override fun visitSimpleNameExpression(expression: KtSimpleNameExpression, context: BindingContext): Void? {
                    val resolvedCall = expression.getResolvedCall(context) ?: return null

                    val resultingDescriptor = resolvedCall.resultingDescriptor
                    if (resultingDescriptor is ReceiverParameterDescriptor) {
                        processExplicitThis(expression, resultingDescriptor)
                        return null
                    }

                    val (extensionReceiver, dispatchReceiver) = resolvedCall
                        .let { (it as? VariableAsFunctionResolvedCall)?.variableCall ?: it }
                        .let { (it.extensionReceiver to it.dispatchReceiver) }
                    if (extensionReceiver != null || dispatchReceiver != null) {
                        val receiverValue = extensionReceiver ?: dispatchReceiver
                        if (receiverValue is ImplicitReceiver) {
                            processImplicitThis(resolvedCall.call.callElement, receiverValue)
                        } else {
                            processThisCall(expression, context, extensionReceiver, dispatchReceiver)
                        }
                    }

                    return null
                }
            })
    }

    private fun findSAMUsages(changeInfo: ChangeInfo, result: MutableSet<UsageInfo>) {
        val method = changeInfo.method
        if (!method.isTrueJavaMethod()) return
        method as PsiMethod

        if (method.containingClass == null) return

        val containingDescriptor = method.getJavaMethodDescriptor()?.containingDeclaration as? JavaClassDescriptor ?: return
        if (containingDescriptor.defaultFunctionTypeForSamInterface == null) return
        val samClass = method.containingClass ?: return

        for (ref in ReferencesSearch.search(samClass)) {
            if (ref !is KtSimpleNameReference) continue

            val callee = ref.expression
            val callExpression = callee.getNonStrictParentOfType<KtCallExpression>() ?: continue
            if (callExpression.calleeExpression !== callee) continue

            val arguments = callExpression.valueArguments
            if (arguments.size != 1) continue

            val argExpression = arguments[0].getArgumentExpression()
            if (argExpression !is KtLambdaExpression) continue

            val context = callExpression.analyze(BodyResolveMode.FULL)

            val functionLiteral = argExpression.functionLiteral
            val functionDescriptor = context.get(BindingContext.FUNCTION, functionLiteral)
            assert(functionDescriptor != null) { "No descriptor for " + functionLiteral.text }

            val samCallType = context.getType(callExpression) ?: continue

            result.add(DeferredJavaMethodOverrideOrSAMUsage(functionLiteral, functionDescriptor!!, samCallType))
        }
    }

    private fun findKotlinOverrides(changeInfo: ChangeInfo, result: MutableSet<UsageInfo>) {
        val method = changeInfo.method as? PsiMethod ?: return
        val methodDescriptor = method.getJavaMethodDescriptor() ?: return

        val baseFunctionInfo = KotlinCallableDefinitionUsage<PsiElement>(method, methodDescriptor, null, null)

        for (overridingMethod in OverridingMethodsSearch.search(method)) {
            val unwrappedElement = overridingMethod.namedUnwrappedElement as? KtNamedFunction ?: continue
            val functionDescriptor = unwrappedElement.resolveToDescriptorIfAny() ?: continue
            result.add(DeferredJavaMethodOverrideOrSAMUsage(unwrappedElement, functionDescriptor, null))
            findDeferredUsagesOfParameters(changeInfo, result, unwrappedElement, functionDescriptor, baseFunctionInfo)
        }
    }

    private fun findKotlinCallers(changeInfo: JavaChangeInfo, result: MutableSet<UsageInfo>) {
        val method = changeInfo.method
        if (!method.isTrueJavaMethod()) return

        for (primaryCaller in changeInfo.methodsToPropagateParameters) {
            addDeferredCallerIfPossible(result, primaryCaller)
            for (overridingCaller in OverridingMethodsSearch.search(primaryCaller)) {
                addDeferredCallerIfPossible(result, overridingCaller)
            }
        }
    }

    private fun addDeferredCallerIfPossible(result: MutableSet<UsageInfo>, overridingCaller: PsiMethod) {
        val unwrappedElement = overridingCaller.namedUnwrappedElement
        if (unwrappedElement is KtFunction || unwrappedElement is KtClass) {
            result.add(DeferredJavaMethodKotlinCallerUsage(unwrappedElement as KtNamedDeclaration))
        }
    }

    private fun findDeferredUsagesOfParameters(
        changeInfo: ChangeInfo,
        result: MutableSet<UsageInfo>,
        function: KtNamedFunction,
        functionDescriptor: FunctionDescriptor,
        baseFunctionInfo: KotlinCallableDefinitionUsage<PsiElement>
    ) {
        val functionInfoForParameters = KotlinCallableDefinitionUsage<PsiElement>(function, functionDescriptor, baseFunctionInfo, null)
        val oldParameters = function.valueParameters
        val parameters = changeInfo.newParameters
        for ((paramIndex, parameterInfo) in parameters.withIndex()) {
            if (!(parameterInfo.oldIndex >= 0 && parameterInfo.oldIndex < oldParameters.size)) continue

            val oldParam = oldParameters[parameterInfo.oldIndex]
            val oldParamName = oldParam.name

            if (!(oldParamName != null && oldParamName != parameterInfo.name)) continue

            for (reference in ReferencesSearch.search(oldParam, oldParam.useScope)) {
                val element = reference.element
                // Usages in named arguments of the calls usage will be changed when the function call is changed
                if (!((element is KtSimpleNameExpression || element is KDocName) && element.parent !is KtValueArgumentName)) continue

                result.add(
                    object : JavaMethodDeferredKotlinUsage<KtElement>(element as KtElement) {
                        override fun resolve(javaMethodChangeInfo: KotlinChangeInfo): JavaMethodKotlinUsageWithDelegate<KtElement> {
                            return object : JavaMethodKotlinUsageWithDelegate<KtElement>(element as KtElement, javaMethodChangeInfo) {
                                override val delegateUsage: KotlinUsageInfo<KtElement>
                                    get() = KotlinParameterUsage(
                                        element as KtElement,
                                        this.javaMethodChangeInfo.newParameters[paramIndex],
                                        functionInfoForParameters
                                    )
                            }
                        }
                    }
                )
            }
        }
    }

    override fun findConflicts(info: ChangeInfo, refUsages: Ref<Array<UsageInfo>>): MultiMap<PsiElement, String> {
        return KotlinChangeSignatureConflictSearcher(info, refUsages).findConflicts()
    }

    private fun isJavaMethodUsage(usageInfo: UsageInfo): Boolean {
        // MoveRenameUsageInfo corresponds to non-Java usage of Java method
        return usageInfo is JavaMethodDeferredKotlinUsage<*> || usageInfo is MoveRenameUsageInfo
    }

    private fun createReplacementUsage(
        originalUsageInfo: UsageInfo,
        javaMethodChangeInfo: KotlinChangeInfo,
        allUsages: Array<UsageInfo>
    ): UsageInfo? {
        if (originalUsageInfo is JavaMethodDeferredKotlinUsage<*>) return originalUsageInfo.resolve(javaMethodChangeInfo)

        val callElement = PsiTreeUtil.getParentOfType(originalUsageInfo.element, KtCallElement::class.java) ?: return null
        val refTarget = originalUsageInfo.reference?.resolve()
        return JavaMethodKotlinCallUsage(callElement, javaMethodChangeInfo, refTarget != null && refTarget.isCaller(allUsages))
    }

    private class NullabilityPropagator(baseMethod: PsiMethod) {
        private val nullManager: NullableNotNullManager
        private val javaPsiFacade: JavaPsiFacade
        private val javaCodeStyleManager: JavaCodeStyleManager
        private val methodAnnotation: PsiAnnotation?
        private val parameterAnnotations: Array<PsiAnnotation?>

        init {
            val project = baseMethod.project
            this.nullManager = NullableNotNullManager.getInstance(project)
            this.javaPsiFacade = JavaPsiFacade.getInstance(project)
            this.javaCodeStyleManager = JavaCodeStyleManager.getInstance(project)

            this.methodAnnotation = getNullabilityAnnotation(baseMethod)
            this.parameterAnnotations = baseMethod.parameterList.parameters.map { getNullabilityAnnotation(it) }.toTypedArray()
        }

        private fun getNullabilityAnnotation(element: PsiModifierListOwner): PsiAnnotation? {
            val nullAnnotation = nullManager.getNullableAnnotation(element, false)
            val notNullAnnotation = nullManager.getNotNullAnnotation(element, false)
            if ((nullAnnotation == null) == (notNullAnnotation == null)) return null
            return nullAnnotation ?: notNullAnnotation
        }

        private fun addNullabilityAnnotationIfApplicable(element: PsiModifierListOwner, annotation: PsiAnnotation?) {
            val nullableAnnotation = nullManager.getNullableAnnotation(element, false)
            val notNullAnnotation = nullManager.getNotNullAnnotation(element, false)

            if (notNullAnnotation != null && nullableAnnotation == null && element is PsiMethod) return

            val annotationQualifiedName = annotation?.qualifiedName
            if (annotationQualifiedName != null && javaPsiFacade.findClass(annotationQualifiedName, element.resolveScope) == null) return

            notNullAnnotation?.delete()
            nullableAnnotation?.delete()

            if (annotationQualifiedName == null) return

            val modifierList = element.modifierList
            if (modifierList != null) {
                modifierList.addAnnotation(annotationQualifiedName)
                javaCodeStyleManager.shortenClassReferences(element)
            }
        }

        fun processMethod(currentMethod: PsiMethod) {
            val currentParameters = currentMethod.parameterList.parameters
            addNullabilityAnnotationIfApplicable(currentMethod, methodAnnotation)
            for (i in parameterAnnotations.indices) {
                addNullabilityAnnotationIfApplicable(currentParameters[i], parameterAnnotations[i])
            }
        }
    }

    private fun isOverriderOrCaller(usage: UsageInfo) = usage is OverriderUsageInfo || usage is CallerUsageInfo


    override fun processUsage(
        changeInfo: ChangeInfo,
        usageInfo: UsageInfo,
        beforeMethodChange: Boolean,
        usages: Array<UsageInfo>,
    ): Boolean {
        if (usageInfo is KotlinWrapperForPropertyInheritorsUsage) return processUsage(
            usageInfo.propertyChangeInfo,
            usageInfo.originalUsageInfo,
            beforeMethodChange,
            usages,
        )

        if (usageInfo is KotlinWrapperForJavaUsageInfos) {
            val kotlinChangeInfo = usageInfo.kotlinChangeInfo
            val javaChangeInfos = kotlinChangeInfo.getOrCreateJavaChangeInfos() ?: return true
            javaChangeInfos.firstOrNull {
                kotlinChangeInfo.originalToCurrentMethods[usageInfo.javaChangeInfo.method] == it.method
            }?.let { javaChangeInfo ->
                val nullabilityPropagator = NullabilityPropagator(javaChangeInfo.method)
                val javaUsageInfos = usageInfo.javaUsageInfos
                val processors = ChangeSignatureUsageProcessor.EP_NAME.extensions

                for (usage in javaUsageInfos) {
                    if (isOverriderOrCaller(usage) && beforeMethodChange) continue
                    for (processor in processors) {
                        if (processor is KotlinChangeSignatureUsageProcessor) continue
                        if (isOverriderOrCaller(usage)) {
                            processor.processUsage(javaChangeInfo, usage, true, javaUsageInfos)
                        }

                        if (processor.processUsage(javaChangeInfo, usage, beforeMethodChange, javaUsageInfos)) break
                    }

                    if (usage is OverriderUsageInfo && usage.isOriginalOverrider) {
                        val overridingMethod = usage.overridingMethod
                        if (overridingMethod != null && overridingMethod !is KtLightMethod) {
                            nullabilityPropagator.processMethod(overridingMethod)
                        }
                    }
                }
            }
        }

        val method = changeInfo.method
        if (beforeMethodChange) {
            if (usageInfo is KotlinUsageInfo<*>) {
                usageInfo.preprocessUsage()
            }

            if (method !is PsiMethod || initializedOriginalDescriptor) return false

            val descriptorWrapper = usages.firstIsInstanceOrNull<OriginalJavaMethodDescriptorWrapper>()
            if (descriptorWrapper == null || descriptorWrapper.originalJavaMethodDescriptor != null) return true

            val baseDeclaration = method.unwrapped ?: return false
            val baseDeclarationDescriptor = method.getJavaOrKotlinMemberDescriptor()?.createDeepCopy() as CallableDescriptor?
                ?: return false

            descriptorWrapper.originalJavaMethodDescriptor = KotlinChangeSignatureData(
                baseDeclarationDescriptor,
                baseDeclaration,
                listOf(baseDeclarationDescriptor)
            )

            // This change info is used as a placeholder before primary method update
            // It gets replaced with real change info afterwards
            val dummyChangeInfo = DummyKotlinChangeInfo(changeInfo.method, descriptorWrapper.originalJavaMethodDescriptor!!)
            for (i in usages.indices) {
                val oldUsageInfo = usages[i]
                if (!isJavaMethodUsage(oldUsageInfo)) continue

                val newUsageInfo = createReplacementUsage(oldUsageInfo, dummyChangeInfo, usages)
                if (newUsageInfo != null) {
                    usages[i] = newUsageInfo
                }
            }

            initializedOriginalDescriptor = true

            return true
        }

        val element = usageInfo.element ?: return false

        if (usageInfo is JavaMethodKotlinUsageWithDelegate<*>) {
            // Do not call getOriginalJavaMethodDescriptorWrapper() on each usage to avoid O(usage_count^2) performance
            if (usageInfo.javaMethodChangeInfo is DummyKotlinChangeInfo) {
                val descriptorWrapper = usages.firstIsInstanceOrNull<OriginalJavaMethodDescriptorWrapper>()
                val methodDescriptor = descriptorWrapper?.originalJavaMethodDescriptor ?: return true

                val resolutionFacade = (methodDescriptor.method as? PsiMethod)?.javaResolutionFacade() ?: return false
                val javaMethodChangeInfo = changeInfo.toJetChangeInfo(methodDescriptor, resolutionFacade)
                for (info in usages) {
                    (info as? JavaMethodKotlinUsageWithDelegate<*>)?.javaMethodChangeInfo = javaMethodChangeInfo
                }
            }

            return usageInfo.processUsage(usages)
        }

        if (usageInfo is MoveRenameUsageInfo && isJavaMethodUsage(usageInfo)) {
            val callee = PsiTreeUtil.getParentOfType(usageInfo.element, KtSimpleNameExpression::class.java, false)
            val ref = callee?.mainReference
            if (ref is KtSimpleNameReference) {
                ref.handleElementRename((method as PsiMethod).name)
                return true
            }

            return false
        }

        @Suppress("UNCHECKED_CAST")
        val kotlinUsageInfo = usageInfo as? KotlinUsageInfo<PsiElement>
        val ktChangeInfo = changeInfo.asKotlinChangeInfo
        return ktChangeInfo != null && kotlinUsageInfo != null && kotlinUsageInfo.processUsage(
            ktChangeInfo,
            element,
            usages,
        )
    }

    override fun processPrimaryMethod(changeInfo: ChangeInfo): Boolean {
        val ktChangeInfo = if (changeInfo is JavaChangeInfo) {
            val method = changeInfo.method as? KtLightMethod ?: return false
            var baseFunction = method.kotlinOrigin ?: return false
            if (baseFunction is KtClass) {
                baseFunction = baseFunction.createPrimaryConstructorIfAbsent()
            }
            val resolutionFacade = baseFunction.getResolutionFacade()
            val baseCallableDescriptor = baseFunction.unsafeResolveToDescriptor() as CallableDescriptor
            if (baseCallableDescriptor !is FunctionDescriptor) {
                return false
            }

            val methodDescriptor = KotlinChangeSignatureData(baseCallableDescriptor, baseFunction, listOf(baseCallableDescriptor))
            val dummyClass = JavaPsiFacade.getElementFactory(method.project).createClass("Dummy")
            val dummyMethod = createJavaMethod(method, dummyClass)
            dummyMethod.containingFile.forcedModuleInfo = baseFunction.getModuleInfo()
            try {
                changeInfo.updateMethod(dummyMethod)
                JavaChangeSignatureUsageProcessor().processPrimaryMethod(changeInfo)
                changeInfo.toJetChangeInfo(methodDescriptor, resolutionFacade)
            } finally {
                changeInfo.updateMethod(method)
            }
        } else {
            changeInfo.asKotlinChangeInfo ?: return false
        }

        for (primaryFunction in ktChangeInfo.methodDescriptor.primaryCallables) {
            primaryFunction.processUsage(ktChangeInfo, primaryFunction.declaration, UsageInfo.EMPTY_ARRAY)
        }

        ktChangeInfo.primaryMethodUpdated()
        return true
    }

    override fun shouldPreviewUsages(changeInfo: ChangeInfo, usages: Array<UsageInfo>) = false

    override fun setupDefaultValues(changeInfo: ChangeInfo, refUsages: Ref<Array<UsageInfo>>, project: Project) = true

    override fun registerConflictResolvers(
        snapshots: MutableList<in ResolveSnapshotProvider.ResolveSnapshot>,
        resolveSnapshotProvider: ResolveSnapshotProvider,
        usages: Array<UsageInfo>, changeInfo: ChangeInfo
    ) {
    }
}
