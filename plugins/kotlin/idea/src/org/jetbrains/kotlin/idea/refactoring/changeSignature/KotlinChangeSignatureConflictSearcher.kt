// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.refactoring.changeSignature

import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.changeSignature.*
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.analysis.analyzeInContext
import org.jetbrains.kotlin.idea.caches.resolve.*
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.refactoring.changeSignature.usages.*
import org.jetbrains.kotlin.idea.refactoring.createTempCopy
import org.jetbrains.kotlin.idea.refactoring.getBodyScope
import org.jetbrains.kotlin.idea.refactoring.getContainingScope
import org.jetbrains.kotlin.idea.refactoring.rename.noReceivers
import org.jetbrains.kotlin.idea.util.*
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.psi.typeRefHelpers.setReceiverTypeReference
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.resolvedCallUtil.getImplicitReceivers
import org.jetbrains.kotlin.resolve.descriptorUtil.classId
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.source.getPsi

class KotlinChangeSignatureConflictSearcher(
    private val originalInfo: ChangeInfo,
    private val refUsages: Ref<Array<UsageInfo>>
) {
    private lateinit var changeInfo: KotlinChangeInfo
    private val result = MultiMap<PsiElement, String>()

    fun findConflicts(): MultiMap<PsiElement, String> {
        // Delete OverriderUsageInfo and CallerUsageInfo for Kotlin declarations since they can't be processed correctly
        // TODO (OverriderUsageInfo only): Drop when OverriderUsageInfo.getElement() gets deleted
        val usageInfos = refUsages.get()
        val adjustedUsages = usageInfos.filterNot { getOverriderOrCaller(it.unwrapped) is KtLightMethod }
        if (adjustedUsages.size < usageInfos.size) {
            refUsages.set(adjustedUsages.toTypedArray())
        }

        changeInfo = originalInfo.asKotlinChangeInfo ?: return result

        doFindConflicts()

        return result
    }

    private fun doFindConflicts() {
        val parameterNames = hashSetOf<String>()
        val function = changeInfo.method
        val bindingContext = (function as KtElement).analyze(BodyResolveMode.FULL)

        // to avoid KT-35903
        val descriptor = bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, function] ?: changeInfo.originalBaseFunctionDescriptor
        val containingDeclaration = descriptor.containingDeclaration

        val parametersScope = when {
            descriptor is ConstructorDescriptor && containingDeclaration is ClassDescriptorWithResolutionScopes -> {
                val classDescriptor = containingDeclaration.classId?.let {
                    function.findModuleDescriptor().findClassAcrossModuleDependencies(it) as? ClassDescriptorWithResolutionScopes
                } ?: containingDeclaration

                classDescriptor.scopeForInitializerResolution
            }

            function is KtFunction -> function.getBodyScope(bindingContext)
            else -> null
        }

        val callableScope = descriptor.getContainingScope()

        val kind = changeInfo.kind
        if (!kind.isConstructor && callableScope != null && changeInfo.newName.isNotEmpty()) {
            val newName = Name.identifier(changeInfo.newName)
            val conflicts = if (descriptor is FunctionDescriptor)
                callableScope.getAllAccessibleFunctions(newName)
            else
                callableScope.getAllAccessibleVariables(newName)

            val newTypes = changeInfo.newParameters.map { it.currentTypeInfo.type }
            for (conflict in conflicts) {
                if (conflict === descriptor) continue

                val conflictElement = DescriptorToSourceUtils.descriptorToDeclaration(conflict)
                if (conflictElement === changeInfo.method) continue

                val candidateTypes = listOfNotNull(conflict.extensionReceiverParameter?.type) + conflict.valueParameters.map { it.type }

                if (candidateTypes == newTypes) {
                    result.putValue(
                        conflictElement,
                        KotlinBundle.message(
                            "text.function.already.exists",
                            DescriptorRenderer.SHORT_NAMES_IN_TYPES.render(conflict)
                        )
                    )

                    break
                }
            }
        }

        val parametersToRemove = changeInfo.parametersToRemove
        if (changeInfo.checkUsedParameters && function is KtCallableDeclaration) {
            checkParametersToDelete(function, parametersToRemove)
        }

        for (parameter in changeInfo.getNonReceiverParameters()) {
            val valOrVar = parameter.valOrVar
            val parameterName = parameter.name

            if (!parameterNames.add(parameterName)) {
                result.putValue(function, KotlinBundle.message("text.duplicating.parameter", parameterName))
            }

            if (parametersScope != null) {
                if (kind === KotlinMethodDescriptor.Kind.PRIMARY_CONSTRUCTOR && valOrVar !== KotlinValVar.None) {
                    for (property in parametersScope.getVariablesFromImplicitReceivers(Name.identifier(parameterName))) {
                        val propertyDeclaration = DescriptorToSourceUtils.descriptorToDeclaration(property) ?: continue
                        if (propertyDeclaration.parent !is KtParameterList) {
                            result.putValue(
                                propertyDeclaration,
                                KotlinBundle.message("text.duplicating.property", parameterName)
                            )

                            break
                        }
                    }
                } else if (function is KtFunction) {
                    for (variable in parametersScope.getContributedVariables(Name.identifier(parameterName), NoLookupLocation.FROM_IDE)) {
                        if (variable is ValueParameterDescriptor) continue
                        val conflictElement = DescriptorToSourceUtils.descriptorToDeclaration(variable)
                        result.putValue(conflictElement, KotlinBundle.message("text.duplicating.local.variable", parameterName))
                    }
                }
            }
        }

        val newReceiverInfo = changeInfo.receiverParameterInfo
        val originalReceiverInfo = changeInfo.methodDescriptor.receiver
        if (function is KtCallableDeclaration && newReceiverInfo != originalReceiverInfo) {
            findReceiverIntroducingConflicts(function, newReceiverInfo)
            findInternalExplicitReceiverConflicts(function, refUsages.get(), originalReceiverInfo)
            findReceiverToParameterInSafeCallsConflicts(refUsages.get())
            findThisLabelConflicts(refUsages, function)
        }

        fun processUsageInfo(usageInfo: UsageInfo) {
            if (usageInfo is KotlinCallerUsage) {
                val namedDeclaration = usageInfo.element
                val callerDescriptor = namedDeclaration?.resolveToDescriptorIfAny() ?: return
                findParameterDuplicationInCaller(namedDeclaration, callerDescriptor)
            }
        }

        val usageInfos = refUsages.get()
        for (usageInfo in usageInfos) {
            when (usageInfo) {
                is KotlinWrapperForPropertyInheritorsUsage -> processUsageInfo(usageInfo.originalUsageInfo)
                is KotlinWrapperForJavaUsageInfos -> findConflictsInJavaUsages(usageInfo)
                is KotlinCallableDefinitionUsage<*> -> {
                    val declaration = usageInfo.declaration as? KtCallableDeclaration ?: continue
                    if (changeInfo.checkUsedParameters) {
                        checkParametersToDelete(declaration, parametersToRemove)
                    }
                }

                else -> processUsageInfo(usageInfo)
            }
        }
    }

    private fun getOverriderOrCaller(usage: UsageInfo): PsiMethod? {
        if (usage is OverriderUsageInfo) return usage.overridingMethod
        if (usage is CallerUsageInfo) {
            val element = usage.element
            return if (element is PsiMethod) element else null
        }

        return null
    }

    private fun checkParametersToDelete(
        callableDeclaration: KtCallableDeclaration,
        toRemove: BooleanArray,
    ) {
        val scope = LocalSearchScope(callableDeclaration)
        val valueParameters = callableDeclaration.valueParameters
        val hasReceiver = valueParameters.size != toRemove.size
        if (hasReceiver && toRemove[0]) {
            findReceiverUsages(callableDeclaration)
        }

        for ((i, parameter) in valueParameters.withIndex()) {
            val index = (if (hasReceiver) 1 else 0) + i
            if (toRemove[index]) {
                registerConflictIfUsed(parameter, scope)
            }
        }
    }

    private fun findReceiverIntroducingConflicts(
        callable: PsiElement,
        newReceiverInfo: KotlinParameterInfo?
    ) {
        if (newReceiverInfo != null && (callable is KtNamedFunction) && callable.bodyExpression != null) {
            val originalContext = callable.analyzeWithContent()

            val noReceiverRefs = ArrayList<KtSimpleNameExpression>()
            callable.forEachDescendantOfType<KtSimpleNameExpression> {
                val resolvedCall = it.getResolvedCall(originalContext) ?: return@forEachDescendantOfType
                if (resolvedCall.noReceivers()) {
                    noReceiverRefs += it
                }
            }

            val psiFactory = KtPsiFactory(callable.project)
            val tempFile = (callable.containingFile as KtFile).createTempCopy()
            val functionWithReceiver = tempFile.findElementAt(callable.textOffset)?.getNonStrictParentOfType<KtNamedFunction>() ?: return
            val receiverTypeRef = psiFactory.createType(newReceiverInfo.currentTypeInfo.render())
            functionWithReceiver.setReceiverTypeReference(receiverTypeRef)
            val newContext = functionWithReceiver.bodyExpression!!.analyze(BodyResolveMode.FULL)

            val originalOffset = callable.bodyExpression!!.textOffset
            val newBody = functionWithReceiver.bodyExpression ?: return
            for (originalRef in noReceiverRefs) {
                val newRef = newBody.findElementAt(originalRef.textOffset - originalOffset)
                    ?.getNonStrictParentOfType<KtReferenceExpression>()

                val newResolvedCall = newRef.getResolvedCall(newContext)
                if (newResolvedCall == null || newResolvedCall.extensionReceiver != null || newResolvedCall.dispatchReceiver != null) {
                    val descriptor = originalRef.getResolvedCall(originalContext)!!.candidateDescriptor
                    val declaration = DescriptorToSourceUtilsIde.getAnyDeclaration(callable.project, descriptor)
                    val prefix = if (declaration != null) RefactoringUIUtil.getDescription(declaration, true) else originalRef.text
                    result.putValue(
                        originalRef,
                        KotlinBundle.message("text.0.will.no.longer.be.accessible.after.signature.change", prefix.replaceFirstChar(Char::uppercaseChar))
                    )
                }
            }
        }
    }

    private fun findInternalExplicitReceiverConflicts(
        function: KtCallableDeclaration,
        usages: Array<UsageInfo>,
        originalReceiverInfo: KotlinParameterInfo?
    ) {
        if (originalReceiverInfo != null) return

        val isObjectFunction = function.containingClassOrObject is KtObjectDeclaration

        loop@ for (usageInfo in usages) {
            if (!(usageInfo is KotlinFunctionCallUsage || usageInfo is KotlinPropertyCallUsage || usageInfo is KotlinByConventionCallUsage)) continue

            val callElement = usageInfo.element as? KtElement ?: continue

            val parent = callElement.parent

            val elementToReport = when {
                usageInfo is KotlinByConventionCallUsage -> callElement
                parent is KtQualifiedExpression && parent.selectorExpression === callElement && !isObjectFunction -> parent
                else -> continue@loop
            }

            val message = KotlinBundle.message(
                "text.explicit.receiver.is.already.present.in.call.element.0",
                CommonRefactoringUtil.htmlEmphasize(elementToReport.text)
            )
            result.putValue(callElement, message)
        }
    }

    private fun findReceiverToParameterInSafeCallsConflicts(
        usages: Array<UsageInfo>
    ) {
        val originalReceiverInfo = changeInfo.methodDescriptor.receiver
        if (originalReceiverInfo == null || originalReceiverInfo !in changeInfo.getNonReceiverParameters()) return

        for (usageInfo in usages) {
            if (!(usageInfo is KotlinFunctionCallUsage || usageInfo is KotlinPropertyCallUsage)) continue

            val callElement = usageInfo.element as? KtElement ?: continue
            val qualifiedExpression = callElement.getQualifiedExpressionForSelector()
            if (qualifiedExpression is KtSafeQualifiedExpression) {
                result.putValue(
                    callElement,
                    KotlinBundle.message(
                        "text.receiver.can.t.be.safely.transformed.to.value.argument",
                        CommonRefactoringUtil.htmlEmphasize(qualifiedExpression.text)
                    )
                )
            }
        }
    }

    private fun findThisLabelConflicts(
        refUsages: Ref<Array<UsageInfo>>,
        callable: KtCallableDeclaration
    ) {
        val psiFactory = KtPsiFactory(callable.project)
        for (usageInfo in refUsages.get()) {
            if (usageInfo !is KotlinParameterUsage) continue

            val newExprText = usageInfo.getReplacementText(changeInfo)
            if (!newExprText.startsWith("this")) continue

            if (usageInfo.element is KDocName) continue // TODO support converting parameter to receiver in KDoc

            val originalExpr = usageInfo.element as? KtExpression ?: continue
            val bindingContext = originalExpr.analyze(BodyResolveMode.FULL)
            val scope = originalExpr.getResolutionScope(bindingContext, originalExpr.getResolutionFacade())

            val newExpr = psiFactory.createExpression(newExprText) as KtThisExpression

            val newContext = newExpr.analyzeInContext(scope, originalExpr)

            val labelExpr = newExpr.getTargetLabel()
            if (labelExpr != null && newContext.get(BindingContext.AMBIGUOUS_LABEL_TARGET, labelExpr) != null) {
                result.putValue(
                    originalExpr,
                    KotlinBundle.message(
                        "text.parameter.reference.can.t.be.safely.replaced.with.0.since.1.is.ambiguous.in.this.context",
                        newExprText,
                        labelExpr.text
                    )
                )
                continue
            }

            val thisTarget = newContext.get(BindingContext.REFERENCE_TARGET, newExpr.instanceReference)
            val thisTargetPsi = (thisTarget as? DeclarationDescriptorWithSource)?.source?.getPsi()
            if (thisTargetPsi != null && callable.isAncestor(thisTargetPsi, true)) {
                result.putValue(
                    originalExpr,
                    KotlinBundle.message(
                        "text.parameter.reference.can.t.be.safely.replaced.with.0.since.target.function.can.t.be.referenced.in.this.context",
                        newExprText
                    )
                )
            }
        }
    }

    private fun findParameterDuplicationInCaller(
        caller: KtNamedDeclaration,
        callerDescriptor: DeclarationDescriptor
    ) {
        val valueParameters = caller.getValueParameters()
        val existingParameters = valueParameters.associateBy { it.name }
        val signature = IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS.render(callerDescriptor)
        for (parameterInfo in changeInfo.getNonReceiverParameters()) {
            if (!(parameterInfo.isNewParameter)) continue

            val name = parameterInfo.name
            val parameter = existingParameters[name] ?: continue

            result.putValue(parameter, KotlinBundle.message("text.there.is.already.a.parameter", name, signature))
        }
    }

    private fun findConflictsInJavaUsages(
        wrapper: KotlinWrapperForJavaUsageInfos,
    ) {
        val kotlinChangeInfo = wrapper.kotlinChangeInfo
        val javaChangeInfo = wrapper.javaChangeInfo
        val javaUsageInfos = wrapper.javaUsageInfos
        val parametersToRemove = javaChangeInfo.toRemoveParm()
        val hasDefaultValue = javaChangeInfo.newParameters.any { !it.defaultValue.isNullOrBlank() }
        val hasDefaultParameter = kotlinChangeInfo.newParameters.any { it.defaultValueAsDefaultParameter }

        for (javaUsage in javaUsageInfos) when (javaUsage) {
            is OverriderUsageInfo -> {
                if (!kotlinChangeInfo.checkUsedParameters) continue

                val javaMethod = javaUsage.overridingMethod
                val baseMethod = javaUsage.baseMethod
                if (baseMethod != javaChangeInfo.method) continue

                JavaChangeSignatureUsageProcessor.ConflictSearcher.checkParametersToDelete(javaMethod, parametersToRemove, result)
            }

            is MethodCallUsageInfo -> {
                val conflictMessage = when {
                    hasDefaultValue -> KotlinBundle.message("change.signature.conflict.text.kotlin.default.value.in.non.kotlin.files")
                    hasDefaultParameter -> KotlinBundle.message("change.signature.conflict.text.kotlin.default.parameter.in.non.kotlin.files")
                    else -> continue
                }

                result.putValue(javaUsage.element, conflictMessage)
            }
        }
    }

    private fun findReceiverUsages(
        callableDeclaration: KtCallableDeclaration,
    ) {
        var hasUsage = false
        callableDeclaration.accept(referenceExpressionRecursiveVisitor(fun(referenceExpression: KtReferenceExpression) {
            if (hasUsage) return

            val context = referenceExpression.analyze(BodyResolveMode.PARTIAL)
            val target = referenceExpression.getResolvedCall(context) ?: return
            val descriptorsToCheck = if (referenceExpression.parent is KtThisExpression)
                listOfNotNull(target.resultingDescriptor as? ReceiverParameterDescriptor)
            else
                target.getImplicitReceivers().mapNotNull { it.getReceiverTargetDescriptor(context) }

            for (descriptor in descriptorsToCheck) {
                val declaration = DescriptorToSourceUtilsIde.getAnyDeclaration(callableDeclaration.project, descriptor) ?: continue
                if (declaration == callableDeclaration || declaration == callableDeclaration.receiverTypeReference) {
                    hasUsage = true
                    return
                }
            }
        }))

        if (hasUsage) {
            result.putValue(
                callableDeclaration.receiverTypeReference,
                KotlinBundle.message("parameter.used.in.declaration.body.warning", KotlinBundle.message("text.receiver")),
            )
        }
    }

    private fun registerConflictIfUsed(
        element: PsiNamedElement,
        scope: LocalSearchScope
    ) {
        if (ReferencesSearch.search(element, scope).findFirst() != null) {
            result.putValue(element, KotlinBundle.message("parameter.used.in.declaration.body.warning", element.name.toString()))
        }
    }
}