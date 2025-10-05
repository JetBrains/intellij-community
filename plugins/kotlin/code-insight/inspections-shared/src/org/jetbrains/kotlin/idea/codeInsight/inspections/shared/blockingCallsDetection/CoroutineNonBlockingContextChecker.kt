// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.blockingCallsDetection

import com.intellij.codeInspection.blockingCallsDetection.ContextType
import com.intellij.codeInspection.blockingCallsDetection.ContextType.*
import com.intellij.codeInspection.blockingCallsDetection.ElementContext
import com.intellij.codeInspection.blockingCallsDetection.NonBlockingContextChecker
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.util.parentsOfType
import com.intellij.util.asSafely
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.expandedSymbol
import org.jetbrains.kotlin.analysis.api.components.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.components.memberScope
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.base.analysis.api.utils.allOverriddenSymbolsWithSelf
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.BLOCKING_EXECUTOR_ANNOTATION
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.COROUTINE_CONTEXT
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.COROUTINE_NAME
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.COROUTINE_SCOPE
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.DEFAULT_DISPATCHER_FQN
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.FLOW_PACKAGE_FQN
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.IO_DISPATCHER_FQN
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.MAIN_DISPATCHER_FQN
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.NONBLOCKING_EXECUTOR_ANNOTATION
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.findFlowOnCall
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypes
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents

internal class CoroutineNonBlockingContextChecker : NonBlockingContextChecker {

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
            analyze(element) {
                val callExpression = containingArgument.getStrictParentOfType<KtCallExpression>() ?: return Blocking
                val call = callExpression.resolveToCall()?.successfulFunctionCallOrNull() ?: return Blocking

                val blockingFriendlyDispatcherUsed = checkBlockingFriendlyDispatcherUsed(call, callExpression)
                if (blockingFriendlyDispatcherUsed.isDefinitelyKnown) return blockingFriendlyDispatcherUsed

                val parameterForArgument = call.argumentMapping[containingLambda] ?: return Blocking
                val type = parameterForArgument.returnType

                if (type is KaFunctionType) {
                    val hasRestrictSuspensionAnnotation = type.receiverType?.isRestrictsSuspensionReceiver() == true
                    return if (!hasRestrictSuspensionAnnotation && type.isSuspend) Unsure else Blocking
                }
            }
        }

        val defaultSuspendContextStatus =
            if (elementContext.inspectionSettings.considerSuspendContextNonBlocking) NonBlocking.INSTANCE else Unsure
        if (containingLambda == null) {
            val isInSuspendFunctionBody = element.parentsOfType<KtNamedFunction>()
                .take(2)
                .firstOrNull { function -> function.nameIdentifier != null }
                ?.hasModifier(KtTokens.SUSPEND_KEYWORD) ?: false
            return if (isInSuspendFunctionBody) defaultSuspendContextStatus else Blocking
        }
        val containingPropertyOrFunction: KtCallableDeclaration? =
            containingLambda.getParentOfTypes(true, KtProperty::class.java, KtNamedFunction::class.java)
        if (containingPropertyOrFunction?.typeReference?.hasModifier(KtTokens.SUSPEND_KEYWORD) == true) return defaultSuspendContextStatus
        return if (containingPropertyOrFunction?.hasModifier(KtTokens.SUSPEND_KEYWORD) == true) defaultSuspendContextStatus else Blocking
    }

    context(_: KaSession)
    private fun checkBlockingFriendlyDispatcherUsed(
        call: KaFunctionCall<*>,
        callExpression: KtCallExpression
    ): ContextType {
        return union(
            { checkBlockFriendlyDispatcherParameter(call) },
            { checkFunctionWithDefaultDispatcher(callExpression) },
            { checkFlowChainElementWithIODispatcher(call, callExpression) }
        )
    }

    private fun getLanguageVersionSettings(psiElement: PsiElement): LanguageVersionSettings =
        psiElement.module?.languageVersionSettings ?: psiElement.project.languageVersionSettings

    context(_: KaSession)
    private fun KaType.isCoroutineContext(): Boolean {
        return this.isSubtypeOf(ClassId.topLevel(COROUTINE_CONTEXT))
    }

    context(_: KaSession)
    private fun checkBlockFriendlyDispatcherParameter(call: KaFunctionCall<*>): ContextType {
        val firstArgument = call.getFirstArgumentExpression()
        val resultArgumentResolvedSymbol = firstArgument?.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()?.symbol ?: return Unsure

        val blockingType = resultArgumentResolvedSymbol.isBlockFriendlyDispatcher()
        if (blockingType != Unsure) return blockingType

        if (isCoroutineContextPlus(resultArgumentResolvedSymbol)) {
            return firstArgument.hasBlockFriendlyDispatcher()
        }
        return Unsure
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
    private fun checkFunctionWithDefaultDispatcher(callExpression: KtCallExpression): ContextType {
        val receiverType = (callExpression.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()
            ?.partiallyAppliedSymbol?.run { dispatchReceiver ?: extensionReceiver } as? KaImplicitReceiverValue)?.type ?: return Unsure

        val coroutineScopeClassId = ClassId.topLevel(COROUTINE_SCOPE)
        if (!receiverType.isSubtypeOf(coroutineScopeClassId)) return Unsure

        val classSymbol = receiverType.symbol as? KaClassSymbol ?: return Unsure
        val propertySymbol = classSymbol.memberScope
            .callables()
            .filterIsInstance<KaPropertySymbol>()
            .singleOrNull { symbol ->
                // TODO isOverridable?
                symbol.returnType.isCoroutineContext()
            } ?: return Unsure

        val initializer = propertySymbol.psi?.asSafely<KtProperty>()?.initializer ?: return Unsure
        return initializer.hasBlockFriendlyDispatcher()
    }

    context(_: KaSession)
    private fun checkFlowChainElementWithIODispatcher(
        call: KaFunctionCall<*>,
        callExpression: KtCallExpression
    ): ContextType {
        val symbol = call.symbol
        val isInsideFlow = symbol.callableId?.asSingleFqName()?.startsWith(FLOW_PACKAGE_FQN) ?: false
        if (!isInsideFlow) return Unsure
        val flowOnCall = callExpression.findFlowOnCall() ?: return NonBlocking.INSTANCE
        return checkBlockFriendlyDispatcherParameter(flowOnCall)
    }

    context(_: KaSession)
    private fun KtExpression.hasBlockFriendlyDispatcher(): ContextType {
        class RecursiveExpressionVisitor : PsiRecursiveElementVisitor() {
            var allowsBlocking: ContextType = Unsure

            override fun visitElement(element: PsiElement) {
                if (element is KtExpression) {
                    val callableSymbol = element.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()?.symbol
                    val allowsBlocking = callableSymbol?.isBlockFriendlyDispatcher()
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

    context(_: KaSession)
    private fun KaCallableSymbol.isBlockFriendlyDispatcher(): ContextType {
        val returnType = returnType

        if (isTypeOrUsageAnnotatedWith(returnType, BLOCKING_EXECUTOR_ANNOTATION)) return Blocking
        if (isTypeOrUsageAnnotatedWith(returnType, NONBLOCKING_EXECUTOR_ANNOTATION)) return NonBlocking.INSTANCE

        if (this is KaConstructorSymbol && containingClassId?.asSingleFqName() == COROUTINE_NAME) return Unsure

        val fqnOrNull = callableId?.asSingleFqName() ?: return Unsure
        return when(fqnOrNull) {
            IO_DISPATCHER_FQN -> Blocking
            MAIN_DISPATCHER_FQN, DEFAULT_DISPATCHER_FQN -> NonBlocking.INSTANCE
            else -> Unsure
        }
    }

    context(_: KaSession)
    private fun isTypeOrUsageAnnotatedWith(type: KaType, annotationFqn: ClassId): Boolean {
        return type.typeOrClassIsAnnotated(annotationFqn)
    }

    private fun union(vararg checks: () -> ContextType): ContextType {
        for (check in checks) {
            val iterationResult = check()
            if (iterationResult != Unsure) return iterationResult
        }
        return Unsure
    }
}

private val RESTRICTS_SUSPENSION_ID: ClassId = ClassId.topLevel(StandardNames.COROUTINES_PACKAGE_FQ_NAME.child(Name.identifier("RestrictsSuspension")))

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