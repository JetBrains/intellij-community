// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.openapi.diagnostic.ControlFlowException
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.idea.FrontendInternals
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.resolve.frontendService
import org.jetbrains.kotlin.idea.util.actionUnderSafeAnalyzeBlock
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.idea.util.returnIfNoDescriptorForDeclarationException
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.BindingTraceContext
import org.jetbrains.kotlin.resolve.DelegatingBindingTrace
import org.jetbrains.kotlin.resolve.bindingContextUtil.getDataFlowInfoBefore
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsStatement
import org.jetbrains.kotlin.resolve.calls.components.InferenceSession
import org.jetbrains.kotlin.resolve.calls.context.ContextDependency
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.lazy.NoDescriptorForDeclarationException
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.expressions.ExpressionTypingServices
import org.jetbrains.kotlin.types.expressions.KotlinTypeInfo
import org.jetbrains.kotlin.types.expressions.PreliminaryDeclarationVisitor

/**
 * This function throws exception when resolveToDescriptorIfAny returns null, otherwise works equivalently.
 */
fun KtDeclaration.unsafeResolveToDescriptor(
    resolutionFacade: ResolutionFacade,
    bodyResolveMode: BodyResolveMode = BodyResolveMode.FULL
): DeclarationDescriptor =
    resolveToDescriptorIfAny(resolutionFacade, bodyResolveMode) ?: throw NoDescriptorForDeclarationException(this)


/**
 * This function first uses declaration resolvers to resolve this declaration and/or additional declarations (e.g. its parent),
 * and then takes the relevant descriptor from binding context.
 * The exact set of declarations to resolve depends on bodyResolveMode
 */
fun KtDeclaration.resolveToDescriptorIfAny(
    resolutionFacade: ResolutionFacade,
    bodyResolveMode: BodyResolveMode = BodyResolveMode.PARTIAL
): DeclarationDescriptor? {
    //TODO: BodyResolveMode.PARTIAL is not quite safe!
    val context = safeAnalyze(resolutionFacade, bodyResolveMode)
    return if (this is KtParameter && hasValOrVar()) {
        context.get(BindingContext.PRIMARY_CONSTRUCTOR_PARAMETER, this)
        // It is incorrect to have `val/var` parameters outside the primary constructor (e.g., `fun foo(val x: Int)`)
        // but we still want to try to resolve in such cases.
            ?: context.get(BindingContext.DECLARATION_TO_DESCRIPTOR, this)
    } else {
        context.get(BindingContext.DECLARATION_TO_DESCRIPTOR, this)
    }
}

fun KtAnnotationEntry.resolveToDescriptorIfAny(
    resolutionFacade: ResolutionFacade,
    bodyResolveMode: BodyResolveMode = BodyResolveMode.PARTIAL
): AnnotationDescriptor? {
    //TODO: BodyResolveMode.PARTIAL is not quite safe!
    val context = safeAnalyze(resolutionFacade, bodyResolveMode)
    return context.get(BindingContext.ANNOTATION, this)
}

fun KtClassOrObject.resolveToDescriptorIfAny(
    resolutionFacade: ResolutionFacade,
    bodyResolveMode: BodyResolveMode = BodyResolveMode.PARTIAL
): ClassDescriptor? {
    return (this as KtDeclaration).resolveToDescriptorIfAny(resolutionFacade, bodyResolveMode) as? ClassDescriptor
}

fun KtNamedFunction.resolveToDescriptorIfAny(
    resolutionFacade: ResolutionFacade,
    bodyResolveMode: BodyResolveMode = BodyResolveMode.PARTIAL
): FunctionDescriptor? {
    return (this as KtDeclaration).resolveToDescriptorIfAny(resolutionFacade, bodyResolveMode) as? FunctionDescriptor
}

fun KtProperty.resolveToDescriptorIfAny(
    resolutionFacade: ResolutionFacade,
    bodyResolveMode: BodyResolveMode = BodyResolveMode.PARTIAL
): VariableDescriptor? {
    return (this as KtDeclaration).resolveToDescriptorIfAny(resolutionFacade, bodyResolveMode) as? VariableDescriptor
}

fun KtParameter.resolveToParameterDescriptorIfAny(
    resolutionFacade: ResolutionFacade,
    bodyResolveMode: BodyResolveMode = BodyResolveMode.PARTIAL
): ValueParameterDescriptor? {
    val context = safeAnalyze(resolutionFacade, bodyResolveMode)
    return context.get(BindingContext.VALUE_PARAMETER, this) as? ValueParameterDescriptor
}

fun KtElement.resolveToCall(
    resolutionFacade: ResolutionFacade,
    bodyResolveMode: BodyResolveMode = BodyResolveMode.PARTIAL
): ResolvedCall<out CallableDescriptor>? = getResolvedCall(safeAnalyze(resolutionFacade, bodyResolveMode))


fun KtElement.safeAnalyze(
    resolutionFacade: ResolutionFacade,
    bodyResolveMode: BodyResolveMode = BodyResolveMode.FULL
): BindingContext = try {
    analyze(resolutionFacade, bodyResolveMode)
} catch (e: Exception) {
    e.returnIfNoDescriptorForDeclarationException { BindingContext.EMPTY }
}

@JvmOverloads
fun KtElement.analyze(
    resolutionFacade: ResolutionFacade,
    bodyResolveMode: BodyResolveMode = BodyResolveMode.FULL
): BindingContext = resolutionFacade.analyze(this, bodyResolveMode)

fun KtElement.analyzeAndGetResult(resolutionFacade: ResolutionFacade): AnalysisResult = try {
    AnalysisResult.success(resolutionFacade.analyze(this), resolutionFacade.moduleDescriptor)
} catch (e: Exception) {
    if (e is ControlFlowException) throw e
    AnalysisResult.internalError(BindingContext.EMPTY, e)
}

// This function is used on declarations to make analysis not only declaration itself but also it content:
// body for declaration with body, initializer & accessors for properties
fun KtElement.analyzeWithContentAndGetResult(resolutionFacade: ResolutionFacade): AnalysisResult =
    resolutionFacade.analyzeWithAllCompilerChecks(this)

// This function is used on declarations to make analysis not only declaration itself but also it content:
// body for declaration with body, initializer & accessors for properties
fun KtDeclaration.analyzeWithContent(resolutionFacade: ResolutionFacade): BindingContext =
    resolutionFacade.analyzeWithAllCompilerChecks(this).bindingContext

// This function is used to make full analysis of declaration container.
// All its declarations, including their content (see above), are analyzed.
inline fun <reified T> T.analyzeWithContent(resolutionFacade: ResolutionFacade): BindingContext where T : KtDeclarationContainer, T : KtElement {
    return resolutionFacade.analyzeWithAllCompilerChecks(this).bindingContext
}

@JvmOverloads
fun KtElement.safeAnalyzeNonSourceRootCode(
    bodyResolveMode: BodyResolveMode = BodyResolveMode.FULL
): BindingContext = safeAnalyzeNonSourceRootCode(getResolutionFacade(), bodyResolveMode)

fun KtElement.safeAnalyzeNonSourceRootCode(
    resolutionFacade: ResolutionFacade,
    bodyResolveMode: BodyResolveMode = BodyResolveMode.FULL
): BindingContext =
    actionUnderSafeAnalyzeBlock({ analyze(resolutionFacade, bodyResolveMode) }, { BindingContext.EMPTY })

fun KtDeclaration.safeAnalyzeWithContentNonSourceRootCode(): BindingContext =
    safeAnalyzeWithContentNonSourceRootCode(getResolutionFacade())

fun KtDeclaration.safeAnalyzeWithContentNonSourceRootCode(
    resolutionFacade: ResolutionFacade,
): BindingContext =
    actionUnderSafeAnalyzeBlock({ analyzeWithContent(resolutionFacade) }, { BindingContext.EMPTY })

@JvmOverloads
@OptIn(FrontendInternals::class)
fun KtExpression.computeTypeInfoInContext(
    scope: LexicalScope,
    contextExpression: KtExpression = this,
    trace: BindingTrace = BindingTraceContext(),
    dataFlowInfo: DataFlowInfo = DataFlowInfo.EMPTY,
    expectedType: KotlinType = TypeUtils.NO_EXPECTED_TYPE,
    isStatement: Boolean = false,
    contextDependency: ContextDependency = ContextDependency.INDEPENDENT,
    expressionTypingServices: ExpressionTypingServices = contextExpression.getResolutionFacade().frontendService<ExpressionTypingServices>()
): KotlinTypeInfo {
    PreliminaryDeclarationVisitor.createForExpression(this, trace, expressionTypingServices.languageVersionSettings)
    return expressionTypingServices.getTypeInfo(
        scope, this, expectedType, dataFlowInfo, InferenceSession.default, trace, isStatement, contextExpression, contextDependency
    )
}

@JvmOverloads
@OptIn(FrontendInternals::class)
fun KtExpression.analyzeInContext(
    scope: LexicalScope,
    contextExpression: KtExpression = this,
    trace: BindingTrace = BindingTraceContext(),
    dataFlowInfo: DataFlowInfo = DataFlowInfo.EMPTY,
    expectedType: KotlinType = TypeUtils.NO_EXPECTED_TYPE,
    isStatement: Boolean = false,
    contextDependency: ContextDependency = ContextDependency.INDEPENDENT,
    expressionTypingServices: ExpressionTypingServices = contextExpression.getResolutionFacade().frontendService<ExpressionTypingServices>()
): BindingContext {
    computeTypeInfoInContext(
        scope,
        contextExpression,
        trace,
        dataFlowInfo,
        expectedType,
        isStatement,
        contextDependency,
        expressionTypingServices
    )
    return trace.bindingContext
}

@JvmOverloads
fun KtExpression.computeTypeInContext(
    scope: LexicalScope,
    contextExpression: KtExpression = this,
    trace: BindingTrace = BindingTraceContext(),
    dataFlowInfo: DataFlowInfo = DataFlowInfo.EMPTY,
    expectedType: KotlinType = TypeUtils.NO_EXPECTED_TYPE
): KotlinType? = computeTypeInfoInContext(scope, contextExpression, trace, dataFlowInfo, expectedType).type

@JvmOverloads
fun KtExpression.analyzeAsReplacement(
    expressionToBeReplaced: KtExpression,
    bindingContext: BindingContext,
    scope: LexicalScope,
    trace: BindingTrace = DelegatingBindingTrace(bindingContext, "Temporary trace for analyzeAsReplacement()"),
    contextDependency: ContextDependency = ContextDependency.INDEPENDENT
): BindingContext = analyzeInContext(
    scope,
    expressionToBeReplaced,
    dataFlowInfo = bindingContext.getDataFlowInfoBefore(expressionToBeReplaced),
    expectedType = bindingContext[BindingContext.EXPECTED_EXPRESSION_TYPE, expressionToBeReplaced] ?: TypeUtils.NO_EXPECTED_TYPE,
    isStatement = expressionToBeReplaced.isUsedAsStatement(bindingContext),
    trace = trace,
    contextDependency = contextDependency
)

@JvmOverloads
fun KtExpression.analyzeAsReplacement(
    expressionToBeReplaced: KtExpression,
    bindingContext: BindingContext,
    resolutionFacade: ResolutionFacade = expressionToBeReplaced.getResolutionFacade(),
    trace: BindingTrace = DelegatingBindingTrace(bindingContext, "Temporary trace for analyzeAsReplacement()"),
    contextDependency: ContextDependency = ContextDependency.INDEPENDENT
): BindingContext {
    val scope = expressionToBeReplaced.getResolutionScope(bindingContext, resolutionFacade)
    return analyzeAsReplacement(expressionToBeReplaced, bindingContext, scope, trace, contextDependency)
}