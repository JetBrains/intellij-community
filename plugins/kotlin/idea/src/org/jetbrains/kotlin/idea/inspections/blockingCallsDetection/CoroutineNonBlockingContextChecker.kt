// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections.blockingCallsDetection

import com.intellij.codeInspection.blockingCallsDetection.ContextType
import com.intellij.codeInspection.blockingCallsDetection.ContextType.*
import com.intellij.codeInspection.blockingCallsDetection.ElementContext
import com.intellij.codeInspection.blockingCallsDetection.NonBlockingContextChecker
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.util.parentsOfType
import com.intellij.util.castSafelyTo
import org.jetbrains.kotlin.builtins.getReceiverTypeFromFunctionType
import org.jetbrains.kotlin.builtins.isBuiltinFunctionalType
import org.jetbrains.kotlin.builtins.isSuspendFunctionType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.core.receiverValue
import org.jetbrains.kotlin.idea.inspections.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.BLOCKING_EXECUTOR_ANNOTATION
import org.jetbrains.kotlin.idea.inspections.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.COROUTINE_CONTEXT
import org.jetbrains.kotlin.idea.inspections.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.COROUTINE_SCOPE
import org.jetbrains.kotlin.idea.inspections.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.DEFAULT_DISPATCHER_FQN
import org.jetbrains.kotlin.idea.inspections.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.FLOW_PACKAGE_FQN
import org.jetbrains.kotlin.idea.inspections.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.IO_DISPATCHER_FQN
import org.jetbrains.kotlin.idea.inspections.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.MAIN_DISPATCHER_FQN
import org.jetbrains.kotlin.idea.inspections.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.NONBLOCKING_EXECUTOR_ANNOTATION
import org.jetbrains.kotlin.idea.inspections.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.findFlowOnCall
import org.jetbrains.kotlin.idea.intentions.getCallableDescriptor
import org.jetbrains.kotlin.idea.project.getLanguageVersionSettings
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.idea.util.projectStructure.module
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypes
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.calls.callUtil.getParameterForArgument
import org.jetbrains.kotlin.resolve.calls.checkers.isRestrictsSuspensionReceiver
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.receivers.ImplicitClassReceiver
import org.jetbrains.kotlin.types.KotlinType

class CoroutineNonBlockingContextChecker : NonBlockingContextChecker {

    override fun isApplicable(file: PsiFile): Boolean {
        if (file !is KtFile) return false

        val languageVersionSettings = getLanguageVersionSettings(file)
        return languageVersionSettings.supportsFeature(LanguageFeature.ReleaseCoroutines)
    }

    override fun computeContextType(elementContext: ElementContext): ContextType {
        val element = elementContext.element
        if (element !is KtCallExpression) return Unsure

        val containingLambda = element.parents
            .filterIsInstance<KtLambdaExpression>()
            .firstOrNull()
        val containingArgument = containingLambda?.getParentOfType<KtValueArgument>(true, KtCallableDeclaration::class.java)
        if (containingArgument != null) {
            val callExpression = containingArgument.getStrictParentOfType<KtCallExpression>() ?: return Blocking
            val call = callExpression.resolveToCall(BodyResolveMode.PARTIAL) ?: return Blocking

            val blockingFriendlyDispatcherUsed = checkBlockingFriendlyDispatcherUsed(call, callExpression)
            if (blockingFriendlyDispatcherUsed.isDefinitelyKnown) return blockingFriendlyDispatcherUsed

            val parameterForArgument = call.getParameterForArgument(containingArgument) ?: return Blocking
            val type = parameterForArgument.returnType ?: return Blocking

            if (type.isBuiltinFunctionalType) {
                val hasRestrictSuspensionAnnotation = type.getReceiverTypeFromFunctionType()?.isRestrictsSuspensionReceiver() ?: false
                return if (!hasRestrictSuspensionAnnotation && type.isSuspendFunctionType) NonBlocking.INSTANCE else Blocking
            }
        }

        if (containingLambda == null) {
            val isInSuspendFunctionBody = element.parentsOfType<KtNamedFunction>()
                .take(2)
                .firstOrNull { function -> function.nameIdentifier != null }
                ?.hasModifier(KtTokens.SUSPEND_KEYWORD) ?: false
            return if (isInSuspendFunctionBody) NonBlocking.INSTANCE else Blocking
        }
        val containingPropertyOrFunction: KtCallableDeclaration? =
            containingLambda.getParentOfTypes(true, KtProperty::class.java, KtNamedFunction::class.java)
        if (containingPropertyOrFunction?.typeReference?.hasModifier(KtTokens.SUSPEND_KEYWORD) == true) return NonBlocking.INSTANCE
        return if (containingPropertyOrFunction?.hasModifier(KtTokens.SUSPEND_KEYWORD) == true) NonBlocking.INSTANCE else Blocking
    }

    private fun checkBlockingFriendlyDispatcherUsed(
        call: ResolvedCall<out CallableDescriptor>,
        callExpression: KtCallExpression
    ): ContextType {
        return union(
            { checkBlockFriendlyDispatcherParameter(call) },
            { checkFunctionWithDefaultDispatcher(callExpression) },
            { checkFlowChainElementWithIODispatcher(call, callExpression) }
        )
    }

    private fun getLanguageVersionSettings(psiElement: PsiElement): LanguageVersionSettings =
        psiElement.module?.languageVersionSettings ?: psiElement.project.getLanguageVersionSettings()

    private fun ResolvedCall<*>.getFirstArgument(): KtExpression? =
        valueArgumentsByIndex?.firstOrNull()?.arguments?.firstOrNull()?.getArgumentExpression()

    private fun KotlinType.isCoroutineContext(): Boolean =
        (this.constructor.supertypes + this).any { it.fqName?.asString() == COROUTINE_CONTEXT }

    private fun checkBlockFriendlyDispatcherParameter(call: ResolvedCall<*>): ContextType {
        val argumentDescriptor = call.getFirstArgument()?.resolveToCall()?.resultingDescriptor ?: return Unsure
        return argumentDescriptor.isBlockFriendlyDispatcher()
    }

    private fun checkFunctionWithDefaultDispatcher(callExpression: KtCallExpression): ContextType {
        val classDescriptor =
            callExpression.receiverValue().castSafelyTo<ImplicitClassReceiver>()?.classDescriptor ?: return Unsure
        if (classDescriptor.typeConstructor.supertypes.none { it.fqName?.asString() == COROUTINE_SCOPE }) return Unsure
        val propertyDescriptor = classDescriptor
            .unsubstitutedMemberScope
            .getContributedDescriptors(DescriptorKindFilter.VARIABLES)
            .filterIsInstance<PropertyDescriptor>()
            .singleOrNull { it.isOverridableOrOverrides && it.type.isCoroutineContext() }
            ?: return Unsure

        val initializer = propertyDescriptor.findPsi().castSafelyTo<KtProperty>()?.initializer ?: return Unsure
        return initializer.hasBlockFriendlyDispatcher()
    }

    private fun checkFlowChainElementWithIODispatcher(
        call: ResolvedCall<out CallableDescriptor>,
        callExpression: KtCallExpression
    ): ContextType {
        val isInsideFlow = call.resultingDescriptor.fqNameSafe.asString().startsWith(FLOW_PACKAGE_FQN)
        if (!isInsideFlow) return Unsure
        val flowOnCall = callExpression.findFlowOnCall() ?: return NonBlocking.INSTANCE
        return checkBlockFriendlyDispatcherParameter(flowOnCall)
    }

    private fun KtExpression.hasBlockFriendlyDispatcher(): ContextType {
        class RecursiveExpressionVisitor : PsiRecursiveElementVisitor() {
            var allowsBlocking: ContextType = Unsure

            override fun visitElement(element: PsiElement) {
                if (element is KtExpression) {
                    val callableDescriptor = element.getCallableDescriptor()
                    val allowsBlocking = callableDescriptor.castSafelyTo<DeclarationDescriptor>()
                        ?.isBlockFriendlyDispatcher()
                    if (allowsBlocking != null && allowsBlocking != Unsure) {
                        this.allowsBlocking = allowsBlocking
                        return
                    }
                }
                super.visitElement(element)
            }
        }

        return RecursiveExpressionVisitor().also(this::accept).allowsBlocking
    }

    private fun DeclarationDescriptor?.isBlockFriendlyDispatcher(): ContextType {
        if (this == null) return Unsure

        val returnTypeDescriptor = this.castSafelyTo<CallableDescriptor>()?.returnType
        val typeConstructor = returnTypeDescriptor?.constructor?.declarationDescriptor

        if (isTypeOrUsageAnnotatedWith(returnTypeDescriptor, typeConstructor, BLOCKING_EXECUTOR_ANNOTATION)) return Blocking
        if (isTypeOrUsageAnnotatedWith(returnTypeDescriptor, typeConstructor, NONBLOCKING_EXECUTOR_ANNOTATION)) return NonBlocking.INSTANCE

        val fqnOrNull = fqNameOrNull()?.asString() ?: return NonBlocking.INSTANCE
        return when(fqnOrNull) {
            IO_DISPATCHER_FQN -> Blocking
            MAIN_DISPATCHER_FQN, DEFAULT_DISPATCHER_FQN -> NonBlocking.INSTANCE
            else -> Unsure
        }
    }

    private fun isTypeOrUsageAnnotatedWith(type: KotlinType?, typeConstructor: ClassifierDescriptor?, annotationFqn: String): Boolean {
        val fqName = FqName(annotationFqn)
        return when {
            type?.annotations?.hasAnnotation(fqName) == true -> true
            typeConstructor?.annotations?.hasAnnotation(fqName) == true -> true
            else -> false
        }
    }

    private fun union(vararg checks: () -> ContextType): ContextType {
        for (check in checks) {
            val iterationResult = check()
            if (iterationResult != Unsure) return iterationResult
        }
        return Unsure
    }
}