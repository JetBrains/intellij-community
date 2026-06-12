// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.refactoring.canRefactorElement
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.types.Variance

internal object CreateContextParameterFromNamedArgumentFixFactory {

    @OptIn(KaExperimentalApi::class)
    val namedParameterNotFound = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NamedParameterNotFound ->
        val argument = diagnostic.psi
        val parameterName = Name.identifierIfValid(diagnostic.name)
            ?: return@ModCommandBased emptyList()
        val argumentExpression = argument.getArgumentExpression() ?: return@ModCommandBased emptyList()

        val callExpression = argument.parent?.parent as? KtCallExpression ?: return@ModCommandBased emptyList()
        val call = callExpression.resolveToCall()?.singleFunctionCallOrNull() ?: return@ModCommandBased emptyList()

        val targetFunction = call.symbol.psi as? KtNamedFunction ?: return@ModCommandBased emptyList()
        if (!targetFunction.canRefactorElement()) return@ModCommandBased emptyList()
        if (targetFunction.contextParameters.any { it.name == parameterName.asString() }) return@ModCommandBased emptyList()

        val argumentType = argumentExpression.expressionType?.takeIf { it !is KaErrorType }
            ?: builtinTypes.any
        val renderedType = argumentType.render(
            renderer = KaTypeRendererForSource.WITH_SHORT_NAMES,
            position = Variance.INVARIANT,
        )

        listOf(
            AddContextParameterFix(
                element = argument,
                contextParameters = listOf(
                    AddContextParameterFix.ContextParameter(name = parameterName, type = renderedType)
                ),
                targetFunctionPointer = targetFunction.createSmartPointer(),
            )
        )
    }
}