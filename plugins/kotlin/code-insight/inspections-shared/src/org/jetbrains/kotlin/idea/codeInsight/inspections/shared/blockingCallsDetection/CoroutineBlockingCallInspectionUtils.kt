// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.blockingCallsDetection

import com.intellij.codeInsight.actions.OptimizeImportsProcessor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.expandedSymbol
import org.jetbrains.kotlin.analysis.api.components.isSuspendFunctionType
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.util.reformatted
import org.jetbrains.kotlin.idea.codeinsight.utils.commitAndUnblockDocument
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import kotlin.collections.get

internal object CoroutineBlockingCallInspectionUtils {

    context(_: KaSession)
    fun isInSuspendLambdaOrFunction(ktElement: KtElement): Boolean {
        val lambdaArgument = ktElement.parentOfType<KtLambdaArgument>()
        if (lambdaArgument != null) {
            val callExpression = lambdaArgument.getStrictParentOfType<KtCallExpression>() ?: return false
            val call = callExpression.resolveToCall()?.successfulCallOrNull<KaFunctionCall<*>>() ?: return false
            val parameterForArgument = call.argumentMapping[lambdaArgument.getArgumentExpression()] ?: return false
            return parameterForArgument.returnType.isSuspendFunctionType
        }

        return ktElement.parentOfType<KtNamedFunction>()?.hasModifier(KtTokens.SUSPEND_KEYWORD) ?: false
    }

    fun isKotlinxOnClasspath(ktElement: KtElement): Boolean {
        val module = ModuleUtilCore.findModuleForPsiElement(ktElement) ?: return false
        val searchScope = GlobalSearchScope.moduleWithLibrariesScope(module)
        return JavaPsiFacade.getInstance(module.project).findClass(DISPATCHERS_FQN.asString(), searchScope) != null
    }

    context(_: KaSession)
    fun isInsideFlowChain(call: KaCall): Boolean {
        if (call !is KaCallableMemberCall<*, *>) return false
        val symbol = call.symbol
        val callableFqName = symbol.callableId?.asSingleFqName()
        val isFlowGenerator = callableFqName?.startsWith(FLOW_PACKAGE_FQN) ?: false

        val receiverType = call.partiallyAppliedSymbol.dispatchReceiver?.type
            ?: call.partiallyAppliedSymbol.extensionReceiver?.type

        val receiverFqName = receiverType?.expandedSymbol?.classId?.asSingleFqName()

        return receiverFqName == FLOW_FQN || (receiverType == null && isFlowGenerator)
    }

    context(_: KaSession)
    fun isCalledInsideNonIoContext(call: KaCall): Boolean {
        val symbol = (call as? KaCallableMemberCall<*, *>)?.symbol ?: return false
        val callFqName = symbol.callableId?.asSingleFqName() ?: return false
        if (callFqName != WITH_CONTEXT_FQN) return false
        return isNonBlockingDispatcher(call)
    }

    context(_: KaSession)
    private fun isNonBlockingDispatcher(call: KaCall): Boolean {
        val dispatcherFqName = call.getFirstArgumentExpression()
            ?.resolveToCall()
            ?.successfulCallOrNull<KaCallableMemberCall<*, *>>()
            ?.symbol
            ?.callableId
            ?.asSingleFqName()
        return dispatcherFqName != null && dispatcherFqName != IO_DISPATCHER_FQN
    }

    fun postProcessQuickFix(replacedElement: KtElement, project: Project) {
        val containingKtFile = replacedElement.containingKtFile
        ShortenReferencesFacility.getInstance().shorten(replacedElement.reformatted() as KtElement)
        OptimizeImportsProcessor(project, containingKtFile).run()
        containingKtFile.commitAndUnblockDocument()
    }

    context(_: KaSession)
    tailrec fun KtExpression.findFlowOnCall(): KaFunctionCall<*>? {
        val dotQualifiedExpression = this.getStrictParentOfType<KtDotQualifiedExpression>() ?: return null
        val candidate = dotQualifiedExpression
            .children
            .asSequence()
            .filterIsInstance<KtCallExpression>()
            .mapNotNull { it.resolveToCall()?.successfulFunctionCallOrNull() }
            .firstOrNull { call ->
                val symbol = call.symbol as? KaCallableSymbol
                symbol?.callableId?.asSingleFqName() == FLOW_ON_FQN
            }
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
    val FLOW_ON_FQN = FqName("kotlinx.coroutines.flow.flowOn")
    val FLOW_PACKAGE_FQN = FqName("kotlinx.coroutines.flow")
    val FLOW_FQN = FqName("kotlinx.coroutines.flow.Flow")
    val WITH_CONTEXT_FQN = FqName("kotlinx.coroutines.withContext")
    val COROUTINE_NAME = FqName("kotlinx.coroutines.CoroutineName")
}

context(_: KaSession)
internal fun KaCall.getFirstArgumentExpression(): KtExpression? {
    if (this !is KaFunctionCall<*>) return null
    val firstValueParameter = partiallyAppliedSymbol.signature.valueParameters.firstOrNull() ?: return null
    return argumentMapping.entries.find { (_, valueParameter) -> valueParameter == firstValueParameter }?.key
}