// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.components.render
import org.jetbrains.kotlin.analysis.api.components.resolveToCallCandidates
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaContextParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.types.Variance
import kotlin.collections.any

@OptIn(KaExperimentalApi::class)
internal object NoContextParameterFixFactory {
    val noContextArgument = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NoContextArgument ->
        val expression = diagnostic.psi as? KtExpression
            ?: return@ModCommandBased emptyList()

        val symbol = diagnostic.symbol as? KaContextParameterSymbol
            ?: return@ModCommandBased emptyList()

        val contextType = symbol.returnType.render(
            renderer = KaTypeRendererForSource.WITH_SHORT_NAMES,
            position = Variance.INVARIANT
        )
        buildList {
            findSurroundingContextCall(expression)?.let {
                add(AddContextParameterToExistingContextFix(it))
            }
            if (expression is KtCallElement) {
                val wrapper = if (expression.languageVersionSettings.apiVersion >= ApiVersion.KOTLIN_2_2) {
                    SurroundCallWithContextFix.Wrapper.CONTEXT
                } else {
                    SurroundCallWithContextFix.Wrapper.WITH
                }
                add(SurroundCallWithContextFix(expression, wrapper))
                addIfNotNull(buildExplicitContextArgumentFix(expression, symbol))
            }
            val containingFunction = expression.getStrictParentOfType<KtNamedFunction>()
            if (containingFunction != null && !containingFunction.hasModifier(KtTokens.OVERRIDE_KEYWORD)) {
                add(AddContextParameterFix(expression, listOf(contextType)))
            }
        }
    }

    private fun findSurroundingContextCall(element: KtElement): KtCallExpression? {
        val lambdaArg = element.getStrictParentOfType<KtLambdaArgument>() ?: return null
        val parentCall = lambdaArg.parent as? KtCallExpression ?: return null
        val callee = parentCall.calleeExpression?.text ?: return null
        if (callee != "context") return null
        return parentCall
    }

    context(_: KaSession)
    private fun buildExplicitContextArgumentFix(
        callElement: KtCallElement,
        currentSymbol: KaContextParameterSymbol,
    ): AddExplicitContextArgumentFix? {
        val candidate = callElement.resolveToCallCandidates()
            .firstNotNullOfOrNull { it.candidate as? KaFunctionCall<*> } ?: return null

        val contextParamSignatures = candidate.signature.contextParameters.ifEmpty { return null }
        val arguments = callElement.valueArgumentList?.arguments.orEmpty()
        val existingArgNames = arguments.mapNotNullTo(hashSetOf()) { it.getArgumentName()?.asName }

        val missingContextParams = contextParamSignatures.filter { it.symbol.name !in existingArgNames }
        if (missingContextParams.isEmpty()) return null

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
                argument.getArgumentExpression()?.expressionType?.isSubtypeOf(paramReturnType) == true
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
                    renderer = KaTypeRendererForSource.WITH_SHORT_NAMES,
                    position = Variance.INVARIANT,
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
