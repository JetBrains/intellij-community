// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections.blockingCallsDetection

import com.intellij.codeInsight.actions.OptimizeImportsProcessor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.builtins.isSuspendFunctionType
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.util.reformatted
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.codeinsight.utils.commitAndUnblockDocument
import org.jetbrains.kotlin.idea.inspections.collections.isCalling
import org.jetbrains.kotlin.idea.intentions.receiverType
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getFirstArgumentExpression
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
        return JavaPsiFacade.getInstance(module.project).findClass(DISPATCHERS_FQN.asString(), searchScope) != null
    }

    fun isInsideFlowChain(resolvedCall: ResolvedCall<*>): Boolean {
        val descriptor = resolvedCall.resultingDescriptor
        val isFlowGenerator = descriptor.fqNameOrNull()?.startsWith(FLOW_PACKAGE_FQN) ?: false
        return descriptor.receiverType()?.fqName == FLOW_FQN || (descriptor.receiverType() == null && isFlowGenerator)
    }

    fun isCalledInsideNonIoContext(resolvedCall: ResolvedCall<*>): Boolean {
        val callFqn = resolvedCall.resultingDescriptor.fqNameSafe
        if (callFqn != WITH_CONTEXT_FQN) return false
        return isNonBlockingDispatcher(resolvedCall)
    }

    private fun isNonBlockingDispatcher(call: ResolvedCall<out CallableDescriptor>): Boolean {
        val dispatcherFqnOrNull = call.getFirstArgumentExpression()
            ?.resolveToCall()
            ?.resultingDescriptor
            ?.fqNameSafe
        return dispatcherFqnOrNull != null && dispatcherFqnOrNull != IO_DISPATCHER_FQN
    }

    fun postProcessQuickFix(replacedElement: KtElement, project: Project) {
        val containingKtFile = replacedElement.containingKtFile
        ShortenReferencesFacility.getInstance().shorten(replacedElement.reformatted() as KtElement)
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
            .firstOrNull { it.isCalling(FLOW_ON_FQN) }
        return candidate ?: dotQualifiedExpression.findFlowOnCall()
    }

    val BLOCKING_EXECUTOR_ANNOTATION = ClassId.topLevel(FqName("org.jetbrains.annotations.BlockingExecutor"))
    val NONBLOCKING_EXECUTOR_ANNOTATION = ClassId.topLevel(FqName("org.jetbrains.annotations.NonBlockingExecutor"))
    private val DISPATCHERS_FQN = FqName("kotlinx.coroutines.Dispatchers")
    val IO_DISPATCHER_FQN = FqName("kotlinx.coroutines.Dispatchers.IO")
    val MAIN_DISPATCHER_FQN = FqName("kotlinx.coroutines.Dispatchers.Main")
    val DEFAULT_DISPATCHER_FQN = FqName("kotlinx.coroutines.Dispatchers.Default")
    val COROUTINE_SCOPE = FqName("kotlinx.coroutines.CoroutineScope")
    val COROUTINE_CONTEXT = FqName("kotlin.coroutines.CoroutineContext")
    private val FLOW_ON_FQN = FqName("kotlinx.coroutines.flow.flowOn")
    val FLOW_PACKAGE_FQN = FqName("kotlinx.coroutines.flow")
    val FLOW_FQN = FqName("kotlinx.coroutines.flow.Flow")
    val WITH_CONTEXT_FQN = FqName("kotlinx.coroutines.withContext")
    val COROUTINE_NAME = FqName("kotlinx.coroutines.CoroutineName")
}