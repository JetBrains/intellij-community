// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections.blockingCallsDetection

import com.intellij.codeInsight.actions.OptimizeImportsProcessor
import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.builtins.isSuspendFunctionType
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.formatter.commitAndUnblockDocument
import org.jetbrains.kotlin.idea.inspections.collections.isCalling
import org.jetbrains.kotlin.idea.intentions.receiverType
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.idea.util.reformatted
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.calls.util.getFirstArgumentExpression
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getParameterForArgument
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

internal object CoroutineBlockingCallInspectionUtils {

    fun isInSuspendLambdaOrFunction(ktElement: KtElement): Boolean {
        val lambdaArgument = ktElement.parentOfType<KtLambdaArgument>()
        if (lambdaArgument != null) {
            val callExpression = lambdaArgument.getStrictParentOfType<KtCallExpression>() ?: return false
            val call = callExpression.resolveToCall(BodyResolveMode.PARTIAL) ?: return false
            val parameterForArgument = call.getParameterForArgument(lambdaArgument) ?: return false
            return parameterForArgument.returnType?.isSuspendFunctionType ?: false
        }

        return ktElement.parentOfType<KtNamedFunction>()?.hasModifier(KtTokens.SUSPEND_KEYWORD) ?: false
    }

    fun isKotlinxOnClasspath(ktElement: KtElement): Boolean {
        val module = ModuleUtilCore.findModuleForPsiElement(ktElement) ?: return false
        val searchScope = GlobalSearchScope.moduleWithLibrariesScope(module)
        return module.project
            .service<JavaPsiFacade>()
            .findClass(DISPATCHERS_FQN, searchScope) != null
    }

    fun isInsideFlowChain(resolvedCall: ResolvedCall<*>): Boolean {
        val descriptor = resolvedCall.resultingDescriptor
        val isFlowGenerator = descriptor.fqNameOrNull()?.asString()?.startsWith(FLOW_PACKAGE_FQN) ?: false
        return descriptor.receiverType()?.fqName?.asString() == FLOW_FQN || (descriptor.receiverType() == null && isFlowGenerator)
    }

    fun isCalledInsideNonIoContext(resolvedCall: ResolvedCall<*>): Boolean {
        val callFqn = resolvedCall.resultingDescriptor?.fqNameSafe?.asString() ?: return false
        if (callFqn != WITH_CONTEXT_FQN) return false
        return isNonBlockingDispatcher(resolvedCall)
    }

    private fun isNonBlockingDispatcher(call: ResolvedCall<out CallableDescriptor>): Boolean {
        val dispatcherFqnOrNull = call.getFirstArgumentExpression()
            ?.resolveToCall()
            ?.resultingDescriptor
            ?.fqNameSafe?.asString()
        return dispatcherFqnOrNull != null && dispatcherFqnOrNull != IO_DISPATCHER_FQN
    }

    fun postProcessQuickFix(replacedElement: KtElement, project: Project) {
        val containingKtFile = replacedElement.containingKtFile
        ShortenReferences.DEFAULT.process(replacedElement.reformatted() as KtElement)
        OptimizeImportsProcessor(project, containingKtFile).run()
        containingKtFile.commitAndUnblockDocument()
    }

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

    const val BLOCKING_EXECUTOR_ANNOTATION = "org.jetbrains.annotations.BlockingExecutor"
    const val NONBLOCKING_EXECUTOR_ANNOTATION = "org.jetbrains.annotations.NonBlockingExecutor"
    const val DISPATCHERS_FQN = "kotlinx.coroutines.Dispatchers"
    const val IO_DISPATCHER_FQN = "kotlinx.coroutines.Dispatchers.IO"
    const val MAIN_DISPATCHER_FQN = "kotlinx.coroutines.Dispatchers.Main"
    const val DEFAULT_DISPATCHER_FQN = "kotlinx.coroutines.Dispatchers.Default"
    const val COROUTINE_SCOPE = "kotlinx.coroutines.CoroutineScope"
    const val COROUTINE_CONTEXT = "kotlin.coroutines.CoroutineContext"
    const val FLOW_ON_FQN = "kotlinx.coroutines.flow.flowOn"
    const val FLOW_PACKAGE_FQN = "kotlinx.coroutines.flow"
    const val FLOW_FQN = "kotlinx.coroutines.flow.Flow"
    const val WITH_CONTEXT_FQN = "kotlinx.coroutines.withContext"
}