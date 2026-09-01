// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ModCommandAction
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaScopeContext
import org.jetbrains.kotlin.analysis.api.components.KaScopeImplicitArgumentValue
import org.jetbrains.kotlin.analysis.api.components.compositeScope
import org.jetbrains.kotlin.analysis.api.components.resolveToCallCandidates
import org.jetbrains.kotlin.analysis.api.components.scopeContext
import org.jetbrains.kotlin.analysis.api.expressions.expressionType
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.render
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.resolution.KaCallCandidateInfo
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.function
import org.jetbrains.kotlin.analysis.api.resolution.single
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.resolution.tryResolveCall
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaContextParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.analysis.api.types.KaSubtypingErrorTypePolicy
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.isSubtypeOf
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.types.Variance

/** A context argument to provide at a call site: a visible value [candidateName],
 *  or (when `null`) a `TODO(...)` placeholder of the given type. */
internal data class ContextArgument(
    val candidateName: String?,
    val typeText: String,
    val typeFqNameText: String,
){
    fun renderExpression(): String =
        candidateName ?: "TODO(\"Provide $typeText\") as $typeFqNameText"
}

@OptIn(KaExperimentalApi::class)
internal object NoContextParameterFixFactory {
    private val CONTEXT_FQ_NAME: FqName = FqName("kotlin.context")
    private val ANONYMOUS_NAME: Name = Name.identifier("_")

    val noContextArgument = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NoContextArgument ->
        val expression = diagnostic.psi as? KtExpression ?: return@ModCommandBased emptyList()
        val symbol = diagnostic.symbol as? KaContextParameterSymbol ?: return@ModCommandBased emptyList()

        // Analysis inputs shared by the fix builders below. Candidate resolution and scope
        // context are not cached per element the way `resolveToCall` is, so compute them once.
        val scopeContext = expression.containingKtFile.scopeContext(expression)
        val callCandidates = if (expression.isInCallableReference()) emptyList() else expression.resolveToCallCandidates()


        // Every fix covers all context types missing at this call site at once, so when the call
        // misses several types (one diagnostic each), emit the fixes only for the first one.
        val unsatisfiedParameters = findUnsatisfiedContextParameters(expression, callCandidates, scopeContext)
        if (unsatisfiedParameters.isNotEmpty() && unsatisfiedParameters.first().symbol != symbol) {
            return@ModCommandBased emptyList()
        }
        // When resolution yields no candidates (e.g. a callable reference in an erroneous call),
        // fall back to the single type from the current diagnostic.
        val missingTypes = unsatisfiedParameters.map { it.returnType }.ifEmpty { listOf(symbol.returnType) }

        buildList {
            addAll(createProvideContextValueFixes(expression, missingTypes, scopeContext))
            addIfNotNull(createExplicitContextArgumentFix(expression, unsatisfiedParameters, callCandidates))
            addIfNotNull(createEnclosingFunctionFix(expression, missingTypes))
        }
    }


    /**
     * Context parameters of the called declaration that are not satisfied at the use site --
     * i.e. those the compiler reports [KaFirDiagnostic.NoContextArgument] for: no matching
     * implicit value is in scope, and no explicit named context argument is present.
     */
    context(session: KaSession)
    private fun findUnsatisfiedContextParameters(
        expression: KtExpression,
        callCandidates: List<KaCallCandidateInfo>,
        scopeContext: KaScopeContext,
    ): List<KaVariableSignature<KaContextParameterSymbol>> {
        val candidate = callCandidates
            .firstNotNullOfOrNull { it.candidate as? KaFunctionCall<*> } ?: return emptyList()
        val contextParameters = candidate.signature.contextParameters.ifEmpty { return emptyList() }

        val explicitArgumentNames = (expression as? KtCallElement)
            ?.valueArgumentList?.arguments.orEmpty()
            .mapNotNullTo(hashSetOf()) { it.getArgumentName()?.asName }
        val implicitTypes = scopeContext.implicitValues
            .filterIsInstance<KaScopeImplicitArgumentValue>()
            .map { it.type }

        return contextParameters.filter { parameter ->
            parameter.symbol.name !in explicitArgumentNames &&
                    implicitTypes.none { it.isSubtypeOf(parameter.returnType) }
        }
    }

    /**
     * Fixes that provide the missing context values at the call site: either by adding arguments
     * to a surrounding `context(...)` call, or by wrapping the expression into a new one.
     * Each missing type takes its sole visible candidate value, or a `TODO(...)` placeholder.
     *
     * The special case: with a single missing type, choosing the value to pass is the entire
     * decision, so one fix is offered per visible candidate instead.
     */
    context(session: KaSession)
    private fun createProvideContextValueFixes(
        expression: KtExpression,
        missingTypes: List<KaType>,
        scopeContext: KaScopeContext,
    ): List<ModCommandAction> {
        val surroundingCall = findSurroundingContextCall(expression)
        val typesToProvide = missingTypes.filterNot { type ->
            surroundingCall != null && innerContextScopeAlreadyContainsType(surroundingCall, type, scopeContext)
        }

        val outerScope = surroundingCall?.let { expression.containingKtFile.scopeContext(it) }

        typesToProvide.singleOrNull()?.let { type ->
            val outerCandidates = outerScope?.let { findValueCandidates(expression, type, it) }.orEmpty()
            val wrapperCandidates = findValueCandidates(expression, type, scopeContext) - outerCandidates
            return buildList {
                outerCandidates.mapTo(this) { name ->
                    provideValueFix(expression, surroundingCall, listOf(contextArgument(name, type)))
                }
                wrapperCandidates.mapTo(this) { name ->
                    provideValueFix(expression, surroundingCall = null, listOf(contextArgument(name, type)))
                }
                if (isEmpty()) {
                    add(provideValueFix(expression, surroundingCall, listOf(contextArgument(null, type))))
                }
            }
        }

        val arguments = typesToProvide.map { type ->
            contextArgument(findValueCandidates(expression, type, outerScope ?: scopeContext).singleOrNull(), type)
        }
        if (arguments.isEmpty()) return emptyList()
        //WITH can only have 1 receiver
        if (surroundingCall == null && contextWrapperFor(expression) == SurroundCallWithContextFix.Wrapper.WITH) {
            return emptyList()
        }
        return listOf(provideValueFix(expression, surroundingCall, arguments))
    }

    private fun provideValueFix(
        expression: KtExpression,
        surroundingCall: KtCallExpression?,
        arguments: List<ContextArgument>,
    ): ModCommandAction =
        if (surroundingCall != null) {
            AddContextParameterToExistingContextFix(surroundingCall, arguments)
        } else {
            SurroundCallWithContextFix(expression, contextWrapperFor(expression), arguments)
        }

    context(session: KaSession)
    private fun contextArgument(candidateName: String?, type: KaType): ContextArgument =
        ContextArgument(
            candidateName = candidateName,
            typeText = type.render(KaTypeRendererForSource.WITH_SHORT_NAMES, Variance.INVARIANT),
            typeFqNameText = type.render(KaTypeRendererForSource.WITH_QUALIFIED_NAMES, Variance.INVARIANT),
        )

    /**
     * Adds anonymous context parameters for all [missingTypes] of the call site to the enclosing function.
     * Deduplication of repeated applications (e.g. "Apply all ... fixes in file" over several call
     * sites missing the same context type) happens at apply time, see [AddContextParameterFix].
     */
    context(session: KaSession)
    private fun createEnclosingFunctionFix(
        expression: KtExpression,
        missingTypes: List<KaType>,
    ): AddContextParameterFix? {
        if (!expression.languageVersionSettings.supportsFeature(LanguageFeature.ContextParameters)) return null
        val containingFunction = expression.getStrictParentOfType<KtNamedFunction>() ?: return null
        if (containingFunction.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return null

        return AddContextParameterFix.ForEnclosingFunction(
            element = expression,
            contextParameters = missingTypes.map { type ->
                AddContextParameterFix.ContextParameter(
                    name = null,
                    type = type.render(KaTypeRendererForSource.WITH_QUALIFIED_NAMES, Variance.INVARIANT),
                    shortType = type.render(KaTypeRendererForSource.WITH_SHORT_NAMES, Variance.INVARIANT),
                )
            },
        )
    }

    private fun KtExpression.isInCallableReference(): Boolean =
        this is KtCallableReferenceExpression || getStrictParentOfType<KtCallableReferenceExpression>() != null


    private fun contextWrapperFor(expression: KtElement): SurroundCallWithContextFix.Wrapper =
        if (expression.languageVersionSettings.apiVersion >= ApiVersion.KOTLIN_2_2) {
            SurroundCallWithContextFix.Wrapper.CONTEXT
        } else {
            SurroundCallWithContextFix.Wrapper.WITH
        }

    @OptIn(KaExperimentalApi::class)
    context(session: KaSession)
    private fun findSurroundingContextCall(element: KtElement): KtCallExpression? {
        val parentCall = element.getStrictParentOfType<KtLambdaArgument>()?.parent as? KtCallExpression ?: return null
        val calleeName = (parentCall.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()
        if (calleeName != CONTEXT_FQ_NAME.shortName().asString()) return null
        val resolvedFqName = parentCall.tryResolveCall()?.single?.function?.symbol?.callableId?.asSingleFqName()
        return if (resolvedFqName == null || resolvedFqName == CONTEXT_FQ_NAME) parentCall else null
    }

    @OptIn(KaExperimentalApi::class)
    context(session: KaSession)
    private fun findValueCandidates(
        useSite: KtElement,
        requiredType: KaType,
        scopeContext: KaScopeContext,
    ): Set<String> {
        return buildSet {
            // Named callables visible at the use site: local vals/vars, parameters,
            // properties of enclosing classes, top-level declarations. Checking inside file, imports pollute candidates.
            scopeContext.compositeScope().callables.forEach { sym ->
                if (sym !is KaVariableSymbol) return@forEach
                if (sym.receiverParameter != null) return@forEach
                val name = sym.name
                if (sym.psi?.containingFile != useSite.containingFile) return@forEach
                if (name == ANONYMOUS_NAME) return@forEach
                if (sym.returnType.isSubtypeOf(requiredType)) add(name.asString())
            }

            // Context parameters of enclosing declarations are exposed as implicit argument values.
            scopeContext.implicitValues.forEach { value ->
                if (value !is KaScopeImplicitArgumentValue) return@forEach
                val name = value.symbol.name
                if (name == ANONYMOUS_NAME) return@forEach
                if (value.type.isSubtypeOf(requiredType)) add(name.asString())
            }
        }
    }

    @OptIn(KaExperimentalApi::class)
    context(session: KaSession)
    private fun innerContextScopeAlreadyContainsType(
        surroundingContextCall: KtCallExpression,
        requiredType: KaType,
        scopeContext: KaScopeContext,
    ): Boolean {
        // Existing positional arguments of context(...).
        val hasMatchingArg = surroundingContextCall.valueArguments
            .mapNotNull { it.getArgumentExpression()?.expressionType }
            .any { it.isSubtypeOf(requiredType) }
        if (hasMatchingArg) return true

        // Context parameters from enclosing declarations propagate into the lambda's context scope.
        return scopeContext.implicitValues
            .filterIsInstance<KaScopeImplicitArgumentValue>()
            .any { it.type.isSubtypeOf(requiredType) }
    }

    context(session: KaSession)
    private fun createExplicitContextArgumentFix(
        expression: KtExpression,
        unsatisfiedParameters: List<KaVariableSignature<KaContextParameterSymbol>>,
        callCandidates: List<KaCallCandidateInfo>,
    ): AddExplicitContextArgumentFix? {
        val callElement = expression as? KtCallElement ?: return null
        if (!callElement.languageVersionSettings.supportsFeature(LanguageFeature.ExplicitContextArguments)) return null
        if (unsatisfiedParameters.isEmpty()) return null
        val candidate = callCandidates
            .firstNotNullOfOrNull { it.candidate as? KaFunctionCall<*> } ?: return null

        // Bail out for _: Anonymous
        if (unsatisfiedParameters.any { it.symbol.name.isSpecial }) return null

        // Skip entirely if any name would clash with a value parameter of some candidate.
        if (unsatisfiedParameters.any { wouldCauseOverloadAmbiguity(callCandidates, it.symbol.name) }) return null

        val arguments = callElement.valueArgumentList?.arguments.orEmpty()
        val existingArgNames = arguments.mapNotNullTo(hashSetOf()) { it.getArgumentName()?.asName }

        // Pool of unnamed arguments we may rename, keeping enough left for required positional value parameters.
        val requiredPositionalCount = candidate.symbol.valueParameters
            .count { !it.hasDefaultValue && !it.isVararg && it.name !in existingArgNames }
        val renamableArguments = arguments.withIndex()
            .filter { (_, arg) -> arg.getArgumentName() == null }
            .toMutableList()

        fun pickRenameTarget(paramReturnType: KaType): IndexedValue<KtValueArgument>? {
            val canSpareOne = renamableArguments.size > requiredPositionalCount
            if (!canSpareOne) return null

            val matchIndex = renamableArguments.indexOfFirst { (_, argument) ->
                argument.getArgumentExpression()?.expressionType?.isSubtypeOf(paramReturnType, KaSubtypingErrorTypePolicy.STRICT) == true
            }
            return if (matchIndex >= 0) renamableArguments.removeAt(matchIndex) else null
        }

        val contextParameterFixes = unsatisfiedParameters.map { paramSignature ->
            val name = paramSignature.symbol.name
            val renameTarget = pickRenameTarget(paramSignature.returnType)

            if (renameTarget != null) {
                AddExplicitContextArgumentFix.ContextParameterFix.AddArgumentName(name, renameTarget.index)
            } else {
                AddExplicitContextArgumentFix.ContextParameterFix.Insert(
                    name = name,
                    argument = contextArgument(candidateName = null, type = paramSignature.returnType),
                )
            }
        }

        return AddExplicitContextArgumentFix(callElement, contextParameterFixes)
    }

    private fun wouldCauseOverloadAmbiguity(
        callCandidates: List<KaCallCandidateInfo>,
        contextParamName: Name,
    ): Boolean {
        return callCandidates.any { candidateInfo ->
            val symbol = (candidateInfo.candidate as? KaFunctionCall<*>)?.symbol ?: return@any false
            symbol.valueParameters.any { it.name == contextParamName }
        }
    }
}