// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.blockingCallsDetection

import com.intellij.codeInspection.blockingCallsDetection.ContextType
import com.intellij.codeInspection.blockingCallsDetection.ContextType.Blocking
import com.intellij.codeInspection.blockingCallsDetection.ContextType.NonBlocking
import com.intellij.codeInspection.blockingCallsDetection.ContextType.Unsure
import com.intellij.codeInspection.blockingCallsDetection.ElementContext
import com.intellij.codeInspection.blockingCallsDetection.NonBlockingContextChecker
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.util.asSafely
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.scopes.memberScope
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.name
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.expandedSymbol
import org.jetbrains.kotlin.analysis.api.types.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.base.analysis.api.utils.allOverriddenSymbolsWithSelf
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.codeInsight.inspections.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.BLOCKING_EXECUTOR_ANNOTATION
import org.jetbrains.kotlin.idea.codeInsight.inspections.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.COROUTINE_CONTEXT
import org.jetbrains.kotlin.idea.codeInsight.inspections.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.COROUTINE_NAME
import org.jetbrains.kotlin.idea.codeInsight.inspections.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.COROUTINE_SCOPE
import org.jetbrains.kotlin.idea.codeInsight.inspections.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.DEFAULT_DISPATCHER_FQN
import org.jetbrains.kotlin.idea.codeInsight.inspections.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.FLOW_PACKAGE_FQN
import org.jetbrains.kotlin.idea.codeInsight.inspections.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.IO_DISPATCHER_FQN
import org.jetbrains.kotlin.idea.codeInsight.inspections.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.MAIN_DISPATCHER_FQN
import org.jetbrains.kotlin.idea.codeInsight.inspections.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.NONBLOCKING_EXECUTOR_ANNOTATION
import org.jetbrains.kotlin.idea.codeInsight.inspections.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.findFlowOnCall
import org.jetbrains.kotlin.idea.codeInsight.inspections.coroutines.getContainingSuspendContext
import org.jetbrains.kotlin.idea.util.resolveSuccessfulExpressionCall
import org.jetbrains.kotlin.idea.util.resolveSuccessfulExpressionSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiUtil

internal class CoroutineNonBlockingContextChecker : NonBlockingContextChecker {

    override fun isApplicable(file: PsiFile): Boolean {
        if (file !is KtFile) return false

        val languageVersionSettings = getLanguageVersionSettings(file)
        return languageVersionSettings.supportsFeature(LanguageFeature.ReleaseCoroutines)
    }

    override fun computeContextType(elementContext: ElementContext): ContextType {
        val element = elementContext.element
        if (element !is KtCallExpression) return Unsure

        val defaultSuspendContextStatus =
            if (elementContext.inspectionSettings.considerSuspendContextNonBlocking) NonBlocking.INSTANCE else Unsure

        analyze(element) {
            val containingSuspendContext = getContainingSuspendContext(element)
            if (containingSuspendContext == null) return Blocking
            if (containingSuspendContext !is KtFunctionLiteral) {
                return defaultSuspendContextStatus
            }

            // For lambdas/anonymous functions, it could be the case that we have a suspend call that should allow blocking calls
            // like `withContext(Dispatchers.IO) { ... }`.
            // This needs to be checked separately, see `getDispatcherType`.
            val lambdaContainer = containingSuspendContext.parent as? KtExpression ?: return defaultSuspendContextStatus
            val callExpression = KtPsiUtil.getParentCallIfPresent(containingSuspendContext) ?: return defaultSuspendContextStatus
            val call = callExpression.resolveSuccessfulExpressionCall() as? KaFunctionCall<*> ?: return Blocking

            // Restricted suspend blocks are always considered blocking.
            if (isRestrictedSuspend(call, lambdaContainer)) return Blocking

            val dispatcherType = getDispatcherType(call, callExpression)
            return dispatcherType.toContextType(defaultSuspendContextStatus)
        }
    }

    context(_: KaSession)
    private fun isRestrictedSuspend(call: KaFunctionCall<*>, lambdaContainer: KtExpression): Boolean {
        val parameterForArgument = call.valueArgumentMapping[lambdaContainer] ?: return false
        val type = parameterForArgument.returnType

        // This ensures that sequences can use blocking code
        return type is KaFunctionType && type.receiverType?.isRestrictsSuspensionReceiver() == true
    }

    private enum class DispatcherType {
        // There is no information about the dispatcher, either because we have nothing to check,
        // or there is no dispatcher in the scope
        NO_INFORMATION,

        // There is a dispatcher in scope, but we do not know whether it is blocking or non-blocking
        UNKNOWN,

        // We have a dispatcher that is definitely non-blocking
        NON_BLOCKING,

        // We have a dispatcher that definitely supports blocking
        BLOCKING;

        fun isDefinite(): Boolean = this == NON_BLOCKING || this == BLOCKING

        fun combine(other: DispatcherType): DispatcherType {
            return maxOf(this, other)
        }

        fun toContextType(defaultType: ContextType): ContextType = when (this) {
            NO_INFORMATION -> defaultType
            UNKNOWN -> Unsure
            NON_BLOCKING -> NonBlocking.INSTANCE
            BLOCKING -> Blocking
        }
    }

    context(_: KaSession)
    private fun getDispatcherType(
        call: KaFunctionCall<*>,
        callExpression: KtExpression
    ): DispatcherType {
        val foundTypes = sequence {
            // Use sequence for laziness
            yield(getContextArgumentDispatcherType(call))
            yield(getCoroutineScopeDispatcherType(callExpression))
            yield(getFlowOnDispatcherType(call, callExpression))
        }

        var computedType = DispatcherType.NO_INFORMATION
        for (type in foundTypes) {
            computedType = computedType.combine(type)
            if (computedType.isDefinite()) return computedType
        }
        return computedType
    }

    private fun getLanguageVersionSettings(psiElement: PsiElement): LanguageVersionSettings =
        psiElement.module?.languageVersionSettings ?: psiElement.project.languageVersionSettings

    context(_: KaSession)
    private fun KaType.isCoroutineContext(): Boolean {
        return this.isSubtypeOf(ClassId.topLevel(COROUTINE_CONTEXT))
    }

    context(_: KaSession)
    private fun getContextArgumentDispatcherType(call: KaFunctionCall<*>): DispatcherType {
        val firstArgument = call.getFirstArgumentExpression()
        val resultArgumentResolvedSymbol =
            firstArgument?.resolveSuccessfulExpressionSymbol() as? KaCallableSymbol ?: return DispatcherType.NO_INFORMATION

        val dispatcherType = resultArgumentResolvedSymbol.toDispatcherType()
        if (dispatcherType != DispatcherType.UNKNOWN) return dispatcherType

        if (isCoroutineContextPlus(resultArgumentResolvedSymbol)) {
            return firstArgument.findNestedDispatcherType()
        }
        return DispatcherType.UNKNOWN
    }

    context(_: KaSession)
    private fun isCoroutineContextPlus(symbol: KaCallableSymbol): Boolean {
        val coroutineContextPlus = CallableId(ClassId.topLevel(COROUTINE_CONTEXT), Name.identifier("plus"))

        if (symbol.name != coroutineContextPlus.callableName) return false
        return symbol.allOverriddenSymbolsWithSelf
            .any { it.callableId == coroutineContextPlus }
    }

    // TODO add testdata to check this function
    context(_: KaSession)
    private fun getCoroutineScopeDispatcherType(callExpression: KtExpression): DispatcherType {
        val receiverType =
            ((callExpression.resolveSuccessfulExpressionCall() as? KaFunctionCall<*>)
                ?.run { dispatchReceiver ?: extensionReceiver } as? KaImplicitReceiverValue)?.type
                ?: return DispatcherType.NO_INFORMATION

        val coroutineScopeClassId = ClassId.topLevel(COROUTINE_SCOPE)
        if (!receiverType.isSubtypeOf(coroutineScopeClassId)) return DispatcherType.NO_INFORMATION

        val classSymbol = receiverType.symbol as? KaClassSymbol ?: return DispatcherType.UNKNOWN
        val propertySymbol = classSymbol.memberScope
            .callables()
            .filterIsInstance<KaPropertySymbol>()
            .singleOrNull { symbol ->
                // TODO isOverridable?
                symbol.returnType.isCoroutineContext()
            } ?: return DispatcherType.UNKNOWN

        val initializer = propertySymbol.psi?.asSafely<KtProperty>()?.initializer ?: return DispatcherType.UNKNOWN
        return initializer.findNestedDispatcherType()
    }

    context(_: KaSession)
    private fun getFlowOnDispatcherType(
        call: KaFunctionCall<*>,
        callExpression: KtExpression
    ): DispatcherType {
        val symbol = call.symbol
        val isInsideFlow = symbol.callableId?.asSingleFqName()?.startsWith(FLOW_PACKAGE_FQN) ?: false
        if (!isInsideFlow) return DispatcherType.NO_INFORMATION
        val flowOnCall = callExpression.findFlowOnCall() ?: return DispatcherType.NON_BLOCKING
        return getContextArgumentDispatcherType(flowOnCall)
    }

    context(_: KaSession)
    private fun KtExpression.findNestedDispatcherType(): DispatcherType {
        class RecursiveExpressionVisitor : PsiRecursiveElementVisitor() {
            var dispatcherType: DispatcherType = DispatcherType.NO_INFORMATION

            override fun visitElement(element: PsiElement) {
                if (element is KtExpression) {
                    val callableSymbol = element.resolveSuccessfulExpressionSymbol() as? KaCallableSymbol
                    val newDispatcherType = callableSymbol?.toDispatcherType()
                    if (newDispatcherType != null) {
                        dispatcherType = maxOf(dispatcherType, newDispatcherType)
                        if (dispatcherType.isDefinite()) {
                            return
                        }
                    }
                }
                super.visitElement(element)
            }
        }

        return RecursiveExpressionVisitor().also(this::accept).dispatcherType
    }

    context(_: KaSession)
    private fun KaCallableSymbol.toDispatcherType(): DispatcherType {
        val returnType = returnType

        if (isTypeOrUsageAnnotatedWith(returnType, BLOCKING_EXECUTOR_ANNOTATION)) return DispatcherType.BLOCKING
        if (isTypeOrUsageAnnotatedWith(returnType, NONBLOCKING_EXECUTOR_ANNOTATION)) return DispatcherType.NON_BLOCKING

        if (this is KaConstructorSymbol && containingClassId?.asSingleFqName() == COROUTINE_NAME) return DispatcherType.UNKNOWN

        val fqnOrNull = callableId?.asSingleFqName() ?: return DispatcherType.UNKNOWN
        return when (fqnOrNull) {
            IO_DISPATCHER_FQN -> DispatcherType.BLOCKING
            MAIN_DISPATCHER_FQN, DEFAULT_DISPATCHER_FQN -> DispatcherType.NON_BLOCKING
            else -> DispatcherType.UNKNOWN
        }
    }

    context(_: KaSession)
    private fun isTypeOrUsageAnnotatedWith(type: KaType, annotationFqn: ClassId): Boolean {
        return type.typeOrClassIsAnnotated(annotationFqn)
    }
}

private val RESTRICTS_SUSPENSION_ID: ClassId =
    ClassId.topLevel(StandardNames.COROUTINES_PACKAGE_FQ_NAME.child(Name.identifier("RestrictsSuspension")))

context(_: KaSession)
private fun KaType.isRestrictsSuspensionReceiver(): Boolean {
    return typeOrClassIsAnnotated(RESTRICTS_SUSPENSION_ID, checkSuperClasses = true)
}

context(_: KaSession)
private fun KaType.typeOrClassIsAnnotated(annotationId: ClassId, checkSuperClasses: Boolean = false): Boolean {
    if (annotations.contains(annotationId)) return true

    val classSymbol = expandedSymbol ?: return false
    if (classSymbol.annotations.contains(annotationId)) return true

    if (!checkSuperClasses) return false
    return classSymbol.superTypes.any { it.typeOrClassIsAnnotated(annotationId, checkSuperClasses) }
}
