// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections.blockingCallsDetection

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
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.isOverridableOrOverrides
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.core.receiverValue
import org.jetbrains.kotlin.idea.inspections.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.BLOCKING_CONTEXT_ANNOTATION
import org.jetbrains.kotlin.idea.inspections.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.COROUTINE_CONTEXT
import org.jetbrains.kotlin.idea.inspections.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.COROUTINE_SCOPE
import org.jetbrains.kotlin.idea.inspections.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.FLOW_PACKAGE_FQN
import org.jetbrains.kotlin.idea.inspections.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.IO_DISPATCHER_FQN
import org.jetbrains.kotlin.idea.inspections.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.NONBLOCKING_CONTEXT_ANNOTATION
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

    override fun isContextNonBlockingFor(elementContext: ElementContext): Boolean {
        val element = elementContext.element
        if (element !is KtCallExpression) return false

        val containingLambda = element.parents
            .filterIsInstance<KtLambdaExpression>()
            .firstOrNull()
        val containingArgument = containingLambda?.getParentOfType<KtValueArgument>(true, KtCallableDeclaration::class.java)
        if (containingArgument != null) {
            val callExpression = containingArgument.getStrictParentOfType<KtCallExpression>() ?: return false
            val call = callExpression.resolveToCall(BodyResolveMode.PARTIAL) ?: return false

            val blockingFriendlyDispatcherUsed = checkBlockingFriendlyDispatcherUsed(call, callExpression)
            if (blockingFriendlyDispatcherUsed.isDefinitelyKnown) {
                return blockingFriendlyDispatcherUsed != BlockingAllowed.DEFINITELY_YES
            }

            val parameterForArgument = call.getParameterForArgument(containingArgument) ?: return false
            val type = parameterForArgument.returnType ?: return false

            if (type.isBuiltinFunctionalType) {
                val hasRestrictSuspensionAnnotation = type.getReceiverTypeFromFunctionType()
                    ?.isRestrictsSuspensionReceiver(getLanguageVersionSettings(element)) ?: false
                return !hasRestrictSuspensionAnnotation && type.isSuspendFunctionType
            }
        }

        if (containingLambda == null) {
            return element.parentsOfType<KtNamedFunction>()
                .take(2)
                .firstOrNull { function -> function.nameIdentifier != null }
                ?.hasModifier(KtTokens.SUSPEND_KEYWORD) ?: false
        }
        val containingPropertyOrFunction: KtCallableDeclaration? =
            containingLambda.getParentOfTypes(true, KtProperty::class.java, KtNamedFunction::class.java)
        if (containingPropertyOrFunction?.typeReference?.hasModifier(KtTokens.SUSPEND_KEYWORD) == true) return true
        return containingPropertyOrFunction?.hasModifier(KtTokens.SUSPEND_KEYWORD) == true
    }

    private fun checkBlockingFriendlyDispatcherUsed(
        call: ResolvedCall<out CallableDescriptor>,
        callExpression: KtCallExpression
    ): BlockingAllowed {
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

    private fun checkBlockFriendlyDispatcherParameter(call: ResolvedCall<*>): BlockingAllowed {
        val argumentDescriptor = call.getFirstArgument()?.resolveToCall()?.resultingDescriptor ?: return BlockingAllowed.UNSURE
        return argumentDescriptor.isBlockFriendlyDispatcher()
    }

    private fun checkFunctionWithDefaultDispatcher(callExpression: KtCallExpression): BlockingAllowed {
        val classDescriptor =
            callExpression.receiverValue().castSafelyTo<ImplicitClassReceiver>()?.classDescriptor ?: return BlockingAllowed.UNSURE
        if (classDescriptor.typeConstructor.supertypes.none { it.fqName?.asString() == COROUTINE_SCOPE }) return BlockingAllowed.UNSURE
        val propertyDescriptor = classDescriptor
            .unsubstitutedMemberScope
            .getContributedDescriptors(DescriptorKindFilter.VARIABLES)
            .filterIsInstance<PropertyDescriptor>()
            .singleOrNull { it.isOverridableOrOverrides && it.type.isCoroutineContext() }
            ?: return BlockingAllowed.UNSURE

        val initializer = propertyDescriptor.findPsi().castSafelyTo<KtProperty>()?.initializer ?: return BlockingAllowed.UNSURE
        return initializer.hasBlockFriendlyDispatcher()
    }

    private fun checkFlowChainElementWithIODispatcher(
        call: ResolvedCall<out CallableDescriptor>,
        callExpression: KtCallExpression
    ): BlockingAllowed {
        val isInsideFlow = call.resultingDescriptor.fqNameSafe.asString().startsWith(FLOW_PACKAGE_FQN)
        if (!isInsideFlow) return BlockingAllowed.UNSURE
        val flowOnCall = callExpression.findFlowOnCall() ?: return BlockingAllowed.DEFINITELY_NO
        return checkBlockFriendlyDispatcherParameter(flowOnCall)
    }

    private fun KtExpression.hasBlockFriendlyDispatcher(): BlockingAllowed {
        class RecursiveExpressionVisitor : PsiRecursiveElementVisitor() {
            var allowsBlocking: BlockingAllowed = BlockingAllowed.UNSURE

            override fun visitElement(element: PsiElement) {
                if (element is KtExpression) {
                    val callableDescriptor = element.getCallableDescriptor()
                    val allowsBlocking = callableDescriptor.castSafelyTo<DeclarationDescriptor>()
                        ?.isBlockFriendlyDispatcher()
                    if (allowsBlocking != null && allowsBlocking != BlockingAllowed.UNSURE) {
                        this.allowsBlocking = allowsBlocking
                        return
                    }
                }
                super.visitElement(element)
            }
        }

        return RecursiveExpressionVisitor().also(this::accept).allowsBlocking
    }

    private fun DeclarationDescriptor?.isBlockFriendlyDispatcher(): BlockingAllowed {
        if (this == null) return BlockingAllowed.UNSURE

        val hasBlockingAnnotation = annotations.hasAnnotation(FqName(BLOCKING_CONTEXT_ANNOTATION))
        if (hasBlockingAnnotation) return BlockingAllowed.DEFINITELY_YES

        val hasNonBlockingAnnotation = annotations.hasAnnotation(FqName(NONBLOCKING_CONTEXT_ANNOTATION))
        if (hasNonBlockingAnnotation) return BlockingAllowed.DEFINITELY_NO

        val fqnOrNull = fqNameOrNull()?.asString() ?: return BlockingAllowed.DEFINITELY_NO
        return if (fqnOrNull == IO_DISPATCHER_FQN) BlockingAllowed.DEFINITELY_YES else BlockingAllowed.DEFINITELY_NO
    }

    private fun union(vararg checks: () -> BlockingAllowed): BlockingAllowed {
        var overallResult = BlockingAllowed.UNSURE
        for (check in checks) {
            val iterationResult = check()
            if (iterationResult != BlockingAllowed.UNSURE) return iterationResult
            overallResult = iterationResult
        }
        return overallResult
    }

    companion object {
        private enum class BlockingAllowed(val isDefinitelyKnown: Boolean) {
            // could also be CONDITIONAL_YES with condition property provided
            DEFINITELY_YES(true),
            DEFINITELY_NO(true),
            UNSURE(false)
        }
    }
}