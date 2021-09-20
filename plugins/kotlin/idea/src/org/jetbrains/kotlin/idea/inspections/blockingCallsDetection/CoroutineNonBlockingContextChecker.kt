// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections.blockingCallsDetection

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
import org.jetbrains.kotlin.idea.inspections.collections.isCalling
import org.jetbrains.kotlin.idea.intentions.getCallableDescriptor
import org.jetbrains.kotlin.idea.project.getLanguageVersionSettings
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.idea.util.projectStructure.module
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
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

    override fun isContextNonBlockingFor(element: PsiElement): Boolean {
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
                return blockingFriendlyDispatcherUsed != BlockingAllowance.DEFINITELY_YES
            }

            val parameterForArgument = call.getParameterForArgument(containingArgument) ?: return false
            val type = parameterForArgument.returnType ?: return false

            if (!type.isBuiltinFunctionalType || !type.isSuspendFunctionType) return false //fixme
            val hasRestrictSuspensionAnnotation = type.getReceiverTypeFromFunctionType()
                ?.isRestrictsSuspensionReceiver(getLanguageVersionSettings(element)) ?: false
            //return hasRestrictSuspensionAnnotation != true && isSuspendFunctionType
            if (hasRestrictSuspensionAnnotation) return false //fixme previously always returned here
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
    ): BlockingAllowance {
        return union( //todo better style?
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


    private fun checkBlockFriendlyDispatcherParameter(call: ResolvedCall<*>): BlockingAllowance {
        val argumentDescriptor = call.getFirstArgument()?.resolveToCall()?.resultingDescriptor ?: return BlockingAllowance.UNKNOWN
        return argumentDescriptor.isBlockFriendlyDispatcher()
    }

    private fun checkFunctionWithDefaultDispatcher(callExpression: KtCallExpression): BlockingAllowance {
        val classDescriptor =
            callExpression.receiverValue().castSafelyTo<ImplicitClassReceiver>()?.classDescriptor ?: return BlockingAllowance.UNKNOWN
        if (classDescriptor.typeConstructor.supertypes.none { it.fqName?.asString() == COROUTINE_SCOPE }) return BlockingAllowance.UNKNOWN
        val propertyDescriptor = classDescriptor
            .unsubstitutedMemberScope
            .getContributedDescriptors(DescriptorKindFilter.VARIABLES)
            .filterIsInstance<PropertyDescriptor>()
            .singleOrNull { it.isOverridableOrOverrides && it.type.isCoroutineContext() }
            ?: return BlockingAllowance.UNKNOWN //fixme?

        val initializer = propertyDescriptor.findPsi().castSafelyTo<KtProperty>()?.initializer ?: return BlockingAllowance.UNKNOWN
        return initializer.hasBlockFriendlyDispatcher()
    }

    private fun checkFlowChainElementWithIODispatcher(
        call: ResolvedCall<out CallableDescriptor>,
        callExpression: KtCallExpression
    ): BlockingAllowance {
        tailrec fun KtExpression.findFlowOnCall(): ResolvedCall<out CallableDescriptor>? {
            val dotQualifiedExpression = this.getStrictParentOfType<KtDotQualifiedExpression>() ?: return null
            val candidate = dotQualifiedExpression
                .children
                .asSequence()
                .filterIsInstance<KtCallExpression>()
                .mapNotNull { it.resolveToCall(BodyResolveMode.PARTIAL) }
                .firstOrNull { it.isCalling(FqName(FLOW_ON_FQN)) }
            return candidate ?: dotQualifiedExpression.findFlowOnCall()
        }

        val isInsideFlow = call.resultingDescriptor.fqNameSafe.startsWith(Name.identifier("kotlinx.coroutines.flow"))
        if (!isInsideFlow) return BlockingAllowance.UNKNOWN
        val flowOnCall = callExpression.findFlowOnCall() ?: return BlockingAllowance.DEFINITELY_NO
        return checkBlockFriendlyDispatcherParameter(flowOnCall)
    }


    private fun KtExpression.hasBlockFriendlyDispatcher(): BlockingAllowance {
        class RecursiveExpressionVisitor : PsiRecursiveElementVisitor() {
            var hasBlockFriendlyDispatcher: BlockingAllowance = BlockingAllowance.UNKNOWN

            override fun visitElement(element: PsiElement) {
                if (element is KtExpression) {
                    val callableDescriptor = element.getCallableDescriptor()
                    if (callableDescriptor.castSafelyTo<DeclarationDescriptor>()
                            ?.isBlockFriendlyDispatcher() == BlockingAllowance.DEFINITELY_YES
                    ) {
                        hasBlockFriendlyDispatcher = BlockingAllowance.DEFINITELY_YES
                        return
                    }
                }
                super.visitElement(element)
            }
        }

        return RecursiveExpressionVisitor().also(this::accept).hasBlockFriendlyDispatcher
    }

    private fun DeclarationDescriptor?.isBlockFriendlyDispatcher(): BlockingAllowance {
        if (this == null) return BlockingAllowance.UNKNOWN

        val hasBlockingAnnotation = this.annotations.hasAnnotation(FqName(BLOCKING_CONTEXT_ANNOTATION))
        if (hasBlockingAnnotation) return BlockingAllowance.DEFINITELY_YES

        val fqnOrNull = this.fqNameOrNull()?.asString() ?: return BlockingAllowance.DEFINITELY_NO //fixme?
        return if (fqnOrNull == IO_DISPATCHER_FQN) BlockingAllowance.DEFINITELY_YES else BlockingAllowance.DEFINITELY_NO
    }

    private fun union(vararg checks: () -> BlockingAllowance): BlockingAllowance { //todo rewrite lazy?
        var overallResult = BlockingAllowance.UNKNOWN
        for (check in checks) {
            val iterationResult = check()
            if (iterationResult != BlockingAllowance.UNKNOWN) return iterationResult
            //if (iterationResult != BlockingAllowance.UNKNOWN) return BlockingAllowance.DEFINITELY_NO
            overallResult = iterationResult
            //overallResult = overallResult.or(iterationResult)
        }
        return overallResult
    }


    companion object {
        private const val BLOCKING_CONTEXT_ANNOTATION = "org.jetbrains.annotations.BlockingContext"
        private const val IO_DISPATCHER_FQN = "kotlinx.coroutines.Dispatchers.IO"
        private const val COROUTINE_SCOPE = "kotlinx.coroutines.CoroutineScope"
        private const val COROUTINE_CONTEXT = "kotlin.coroutines.CoroutineContext"
        private const val FLOW_ON_FQN = "kotlinx.coroutines.flow.flowOn"

        private enum class BlockingAllowance(val isDefinitelyKnown: Boolean) { // could also be CONDITIONAL_YES with condition property provided
            DEFINITELY_YES(true),
            DEFINITELY_NO(true),
            UNKNOWN(false);

            fun or(other: BlockingAllowance): BlockingAllowance {
                return when (this) {
                    DEFINITELY_YES -> DEFINITELY_YES
                    UNKNOWN -> {
                        if (other == DEFINITELY_YES)
                            DEFINITELY_YES
                        else
                            UNKNOWN
                    }
                    DEFINITELY_NO -> other
                }
            }

            fun and(other: BlockingAllowance): BlockingAllowance {
                return when (this) {
                    DEFINITELY_NO -> DEFINITELY_NO
                    UNKNOWN -> other
                    DEFINITELY_YES -> {
                        if (other == UNKNOWN)
                            DEFINITELY_YES
                        else
                            other
                    }
                }
            }
        }
    }
}