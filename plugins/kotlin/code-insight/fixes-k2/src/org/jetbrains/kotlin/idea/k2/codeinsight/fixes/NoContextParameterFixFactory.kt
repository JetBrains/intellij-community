// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ModCommandAction
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaScopeImplicitArgumentValue
import org.jetbrains.kotlin.analysis.api.components.compositeScope
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.components.resolveToCallCandidates
import org.jetbrains.kotlin.analysis.api.components.scopeContext
import org.jetbrains.kotlin.analysis.api.expressions.expressionType
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.render
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
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
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.types.Variance

@OptIn(KaExperimentalApi::class)
internal object NoContextParameterFixFactory {
    private val CONTEXT_FQ_NAME: FqName = FqName("kotlin.context")
    private val ANONYMOUS_NAME: Name = Name.identifier("_")

    val noContextArgument = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NoContextArgument ->
        val expression = diagnostic.psi as? KtExpression ?: return@ModCommandBased emptyList()
        val symbol = diagnostic.symbol as? KaContextParameterSymbol ?: return@ModCommandBased emptyList()
        val requiredType = symbol.returnType

        buildList {
            addAll(createProvideContextValueFixes(expression, requiredType))
            addIfNotNull(createExplicitContextArgumentFix(expression, symbol))
            addIfNotNull(createEnclosingFunctionFix(expression, requiredType))
        }
    }

    /**
     * Fixes that provide the missing context value at the call site: either by adding an argument
     * to a surrounding `context(...)` call, or by wrapping the expression into a new one.
     * One fix per visible candidate value; candidates visible only inside the surrounding lambda
     * are offered as a new wrapper instead. Without candidates, a single fix with a `TODO(...)`
     * placeholder argument (represented by a `null` candidate name).
     */
    context(session: KaSession)
    private fun createProvideContextValueFixes(
        expression: KtExpression,
        requiredType: KaType,
    ): List<ModCommandAction> {
        val typeText = requiredType.render(KaTypeRendererForSource.WITH_SHORT_NAMES, Variance.INVARIANT)
        val typeFqNameText = requiredType.render(KaTypeRendererForSource.WITH_QUALIFIED_NAMES, Variance.INVARIANT)

        val surroundingCall = findSurroundingContextCall(expression)
        val alreadyContainsType = surroundingCall != null &&
                innerContextScopeAlreadyContainsType(expression, surroundingCall, requiredType)

        val innerCandidates = if (alreadyContainsType) emptySet() else findValueCandidates(expression, expression, requiredType)

        if (surroundingCall == null) {
            val wrapper = contextWrapperFor(expression)
            val candidateNames: Collection<String?> = innerCandidates.ifEmpty { listOf(null) }
            return candidateNames.map { name ->
                SurroundCallWithContextFix(expression, wrapper, name, typeText, typeFqNameText)
            }
        }

        val outerCandidates = if (alreadyContainsType) emptySet() else findValueCandidates(expression, surroundingCall, requiredType)
        val nestedOnlyCandidates = innerCandidates - outerCandidates

        return buildList {
            outerCandidates.mapTo(this) { candidateName ->
                AddContextParameterToExistingContextFix(surroundingCall, candidateName, typeText, typeFqNameText)
            }
            if (nestedOnlyCandidates.isNotEmpty()) {
                val wrapper = contextWrapperFor(expression)
                nestedOnlyCandidates.mapTo(this) { candidateName ->
                    SurroundCallWithContextFix(expression, wrapper, candidateName, typeText, typeFqNameText)
                }
            }
            if (isEmpty()) {
                add(AddContextParameterToExistingContextFix(surroundingCall, null, typeText, typeFqNameText))
            }
        }
    }

    /**
     * Adds an anonymous context parameter of the required type to the enclosing function.
     */
    context(session: KaSession)
    private fun createEnclosingFunctionFix(
        expression: KtExpression,
        requiredType: KaType,
    ): AddContextParameterFix? {
        val containingFunction = expression.getStrictParentOfType<KtNamedFunction>() ?: return null
        if (containingFunction.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return null

        return AddContextParameterFix.ForEnclosingFunction(
            element = expression,
            contextParameter = AddContextParameterFix.ContextParameter(
                name = null,
                type = requiredType.render(KaTypeRendererForSource.WITH_QUALIFIED_NAMES, Variance.INVARIANT),
                shortType = requiredType.render(KaTypeRendererForSource.WITH_SHORT_NAMES, Variance.INVARIANT),
            )
        )
    }

    private fun contextWrapperFor(expression: KtElement): SurroundCallWithContextFix.Wrapper =
        if (expression.languageVersionSettings.apiVersion >= ApiVersion.KOTLIN_2_2) {
            SurroundCallWithContextFix.Wrapper.CONTEXT
        } else {
            SurroundCallWithContextFix.Wrapper.WITH
        }

    context(session: KaSession)
    private fun findSurroundingContextCall(element: KtElement): KtCallExpression? {
        val parentCall = element.getStrictParentOfType<KtLambdaArgument>()?.parent as? KtCallExpression ?: return null
        val calleeName = (parentCall.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()
        if (calleeName != CONTEXT_FQ_NAME.shortName().asString()) return null
        val resolvedFqName = parentCall.resolveToCall()?.singleFunctionCallOrNull()?.symbol?.callableId?.asSingleFqName()
        return if (resolvedFqName == null || resolvedFqName == CONTEXT_FQ_NAME) parentCall else null
    }

    /**
     * Collects names of in-scope values whose type satisfies [requiredType].
     *
     * [scopeAnchor] seeds the `KaScopeContext` — pass the use-site to include locals declared
     * inside a surrounding lambda, or the surrounding call to see only what's visible *outside*
     * that lambda. [useSite] is only used to filter out symbols from other files (imports pollute).
     */
    context(session: KaSession)
    private fun findValueCandidates(
        useSite: KtElement,
        scopeAnchor: KtElement,
        requiredType: KaType,
    ): Set<String> {
        val scopeContext = scopeAnchor.containingKtFile.scopeContext(scopeAnchor)
        return buildSet {
            // Named callables visible at the anchor: local vals/vars, parameters,
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

    context(session: KaSession)
    private fun innerContextScopeAlreadyContainsType(
        useSite: KtElement,
        surroundingContextCall: KtCallExpression,
        requiredType: KaType,
    ): Boolean {
        // Existing positional arguments of context(...).
        val hasMatchingArg = surroundingContextCall.valueArguments
            .mapNotNull { it.getArgumentExpression()?.expressionType }
            .any { it.isSubtypeOf(requiredType) }
        if (hasMatchingArg) return true

        // Context parameters from enclosing declarations propagate into the lambda's context scope.
        return useSite.containingKtFile.scopeContext(useSite).implicitValues
            .filterIsInstance<KaScopeImplicitArgumentValue>()
            .any { it.type.isSubtypeOf(requiredType) }
    }

    context(_: KaSession)
    private fun createExplicitContextArgumentFix(
        expression: KtExpression,
        currentSymbol: KaContextParameterSymbol,
    ): AddExplicitContextArgumentFix? {
        val callElement = expression as? KtCallElement ?: return null
        if (!callElement.languageVersionSettings.supportsFeature(LanguageFeature.ExplicitContextArguments)) return null

        val candidate = callElement.resolveToCallCandidates()
            .firstNotNullOfOrNull { it.candidate as? KaFunctionCall<*> } ?: return null

        val contextParamSignatures = candidate.signature.contextParameters.ifEmpty { return null }
        val arguments = callElement.valueArgumentList?.arguments.orEmpty()
        val existingArgNames = arguments.mapNotNullTo(hashSetOf()) { it.getArgumentName()?.asName }

        val missingContextParams = contextParamSignatures.filter { it.symbol.name !in existingArgNames }
        if (missingContextParams.isEmpty()) return null
        // Bail out for _: Anonymous
        if (missingContextParams.any { it.symbol.name.isSpecial }) return null

        // Emit the fix only once per call site.
        if (missingContextParams.first().symbol.name != currentSymbol.name) return null

        // Skip entirely if any name would clash with a value parameter of some candidate.
        if (missingContextParams.any { wouldCauseOverloadAmbiguity(callElement, it.symbol.name) }) return null

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

        val contextParameterFixes = missingContextParams.map { paramSignature ->
            val name = paramSignature.symbol.name
            val renameTarget = pickRenameTarget(paramSignature.returnType)

            if (renameTarget != null) {
                AddExplicitContextArgumentFix.ContextParameterFix.AddArgumentName(name, renameTarget.index)
            } else {
                val type = paramSignature.returnType.render(
                    KaTypeRendererForSource.WITH_SHORT_NAMES,
                    Variance.INVARIANT
                )
                AddExplicitContextArgumentFix.ContextParameterFix.Insert(name, type)
            }
        }

        return AddExplicitContextArgumentFix(callElement, contextParameterFixes)
    }

    context(_: KaSession)
    private fun wouldCauseOverloadAmbiguity(
        callElement: KtCallElement,
        contextParamName: Name,
    ): Boolean {
        return callElement.resolveToCallCandidates().any { candidateInfo ->
            val symbol = (candidateInfo.candidate as? KaFunctionCall<*>)?.symbol ?: return@any false
            symbol.valueParameters.any { it.name == contextParamName }
        }
    }
}