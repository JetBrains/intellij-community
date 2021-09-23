// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections.blockingCallsDetection

import com.intellij.codeInsight.actions.OptimizeImportsProcessor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.formatter.commitAndUnblockDocument
import org.jetbrains.kotlin.idea.inspections.collections.isCalling
import org.jetbrains.kotlin.idea.intentions.receiverType
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.idea.util.reformatted
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.calls.callUtil.getFirstArgumentExpression
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

internal object CoroutineBlockingCallInspectionUtils {

    fun isInsideFlowChain(resolvedCall: ResolvedCall<*>): Boolean {
        val descriptor = resolvedCall.resultingDescriptor
        val isFlowGenerator = descriptor.fqNameOrNull()?.asString()?.startsWith(FLOW_PACKAGE_FQN) ?: false
        return descriptor.receiverType()?.fqName?.asString() == FLOW_FQN || (descriptor.receiverType() == null && isFlowGenerator)
    }

    fun isCalledInsideNonIoContext(resolvedCall: ResolvedCall<*>): Boolean {
        val callFqn = resolvedCall.resultingDescriptor?.fqNameSafe?.asString() ?: return false
        if (callFqn != "kotlinx.coroutines.withContext") return false
        return isNonBlockingDispatcher(resolvedCall)
    }

    private fun isNonBlockingDispatcher(call: ResolvedCall<out CallableDescriptor>): Boolean {
        val dispatcherFqnOrNull = call.getFirstArgumentExpression()
            ?.resolveToCall()
            ?.resultingDescriptor
            ?.fqNameSafe?.asString()
        return dispatcherFqnOrNull != null && dispatcherFqnOrNull != "kotlinx.coroutines.Dispatchers.IO"
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

    const val BLOCKING_CONTEXT_ANNOTATION = "org.jetbrains.annotations.BlockingContext"
    const val NONBLOCKING_CONTEXT_ANNOTATION = "org.jetbrains.annotations.NonBlockingContext"
    const val IO_DISPATCHER_FQN = "kotlinx.coroutines.Dispatchers.IO"
    const val COROUTINE_SCOPE = "kotlinx.coroutines.CoroutineScope"
    const val COROUTINE_CONTEXT = "kotlin.coroutines.CoroutineContext"
    const val FLOW_ON_FQN = "kotlinx.coroutines.flow.flowOn"
    const val FLOW_PACKAGE_FQN = "kotlinx.coroutines.flow"
    const val FLOW_FQN = "kotlinx.coroutines.flow.Flow"
}