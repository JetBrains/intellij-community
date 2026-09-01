// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.blockingCallsDetection

import com.intellij.codeInsight.actions.OptimizeImportsProcessor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.KaSimpleCall
import org.jetbrains.kotlin.analysis.api.resolution.KaSimpleOrMultiCall
import org.jetbrains.kotlin.analysis.api.resolution.resolveSuccessfulCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.types.expandedSymbol
import org.jetbrains.kotlin.analysis.api.types.isSuspendFunctionType
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.util.reformatted
import org.jetbrains.kotlin.idea.codeinsight.utils.commitAndUnblockDocument
import org.jetbrains.kotlin.idea.util.resolveSuccessfulExpressionSymbol
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal object CoroutineBlockingCallInspectionUtils {

    @OptIn(KaExperimentalApi::class)
    context(_: KaSession)
    fun isInSuspendLambdaOrFunction(ktElement: KtElement): Boolean {
        val lambdaArgument = ktElement.parentOfType<KtLambdaArgument>()
        if (lambdaArgument != null) {
            val callExpression = lambdaArgument.getStrictParentOfType<KtCallExpression>() ?: return false
            val call: KaFunctionCall<*> = callExpression.resolveSuccessfulCall() ?: return false
            val parameterForArgument = call.valueArgumentMapping[lambdaArgument.getArgumentExpression()] ?: return false
            return parameterForArgument.returnType.isSuspendFunctionType
        }

        return ktElement.parentOfType<KtNamedFunction>()?.hasModifier(KtTokens.SUSPEND_KEYWORD) ?: false
    }

    fun isKotlinxOnClasspath(ktElement: KtElement): Boolean {
        val module = ModuleUtilCore.findModuleForPsiElement(ktElement) ?: return false
        val searchScope = GlobalSearchScope.moduleWithLibrariesScope(module)
        return JavaPsiFacade.getInstance(module.project).findClass(DISPATCHERS_FQN.asString(), searchScope) != null
    }

    @OptIn(KaExperimentalApi::class)
    context(_: KaSession)
    fun isInsideFlowChain(call: KaSimpleCall<*, *>): Boolean {
        val symbol = call.symbol
        val callableFqName = symbol.callableId?.asSingleFqName()
        val isFlowGenerator = callableFqName?.startsWith(FLOW_PACKAGE_FQN) ?: false

        val receiverType = call.dispatchReceiver?.type ?: call.extensionReceiver?.type

        val receiverFqName = receiverType?.expandedSymbol?.classId?.asSingleFqName()

        return receiverFqName == FLOW_FQN || (receiverType == null && isFlowGenerator)
    }

    @OptIn(KaExperimentalApi::class)
    context(_: KaSession)
    fun isCalledInsideNonIoContext(call: KaSimpleOrMultiCall): Boolean {
        val symbol = (call as? KaSimpleCall<*, *>)?.symbol ?: return false
        val callFqName = symbol.callableId?.asSingleFqName() ?: return false
        if (callFqName != WITH_CONTEXT_FQN) return false
        return isNonBlockingDispatcher(call)
    }

    @OptIn(KaExperimentalApi::class)
    context(_: KaSession)
    private fun isNonBlockingDispatcher(call: KaSimpleCall<*, *>): Boolean {
        val dispatcherFqName = (call.getFirstArgumentExpression()?.resolveSuccessfulExpressionSymbol() as? KaCallableSymbol)
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

    @OptIn(KaExperimentalApi::class)
    context(_: KaSession)
    tailrec fun KtExpression.findFlowOnCall(): KaFunctionCall<*>? {
        val dotQualifiedExpression = this.getStrictParentOfType<KtDotQualifiedExpression>() ?: return null
        val candidate = dotQualifiedExpression
            .children
            .asSequence()
            .filterIsInstance<KtCallExpression>()
            .mapNotNull { it.resolveSuccessfulCall() }
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

@OptIn(KaExperimentalApi::class)
context(_: KaSession)
internal fun KaSimpleCall<*, *>.getFirstArgumentExpression(): KtExpression? {
    if (this !is KaFunctionCall<*>) return null
    val firstValueParameter = signature.valueParameters.firstOrNull() ?: return null
    return valueArgumentMapping.entries.find { (_, valueParameter) -> valueParameter == firstValueParameter }?.key
}
