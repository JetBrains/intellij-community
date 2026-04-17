// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToCallCandidates
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaContextParameterSymbol
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtNamedFunction
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

        val contextName = symbol.name.asString()
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
                add(SurroundCallWithContextFix(expression, wrapper))
                if (!wouldCauseOverloadAmbiguity(expression, contextName)) {
                    add(
                        AddExplicitContextArgumentFix(
                            expression,
                            listOf(AddExplicitContextArgumentFix.ContextParameterInfo(contextName, contextType))
                        )
                    )
                }
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
    private fun wouldCauseOverloadAmbiguity(
        callElement: KtExpression,
        contextParamName: String,
    ): Boolean {
        return callElement.resolveToCallCandidates().any { candidateInfo ->
            val symbol = (candidateInfo.candidate as? KaFunctionCall<*>)?.symbol ?: return@any false
            symbol.valueParameters.any { it.name.asString() == contextParamName }
        }
    }
}
