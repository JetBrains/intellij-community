// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameValidatorProvider
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal class ReplaceUnderscoreWithParameterNameIntention :
    KotlinApplicableModCommandAction<KtCallableDeclaration, String>(KtCallableDeclaration::class) {

    override fun getFamilyName(): String = KotlinBundle.message("replace.with.parameter.name")

    override fun isApplicableByPsi(element: KtCallableDeclaration): Boolean =
        element.name == "_" && (element is KtDestructuringDeclarationEntry || element is KtParameter)

    override fun KaSession.prepareContext(element: KtCallableDeclaration): String? {
        val validator = KotlinNameValidatorProvider.getInstance().createNameValidator(
            container = element.parent?.parent ?: element,
            target = KotlinNameSuggestionProvider.ValidatorTarget.PARAMETER,
        )

        val suggestedName = when (element) {
            is KtDestructuringDeclarationEntry -> dataClassParameterName(element)
            is KtParameter if (element.isLambdaParameter) -> lambdaParameterName(element)
            is KtParameter -> KotlinNameSuggester().suggestTypeNames(element.symbol.returnType).firstOrNull()
            else -> null
        } ?: return null

        return KotlinNameSuggester.suggestNameByName(suggestedName, validator)
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtCallableDeclaration,
        elementContext: String,
        updater: ModPsiUpdater,
    ) {
        element.setName(elementContext)
    }
}

private fun KaSession.dataClassParameterName(declarationEntry: KtDestructuringDeclarationEntry): String? {
    val declaration = declarationEntry.parent as? KtDestructuringDeclaration ?: return null
    val entryIndex = declaration.entries.indexOf(declarationEntry).takeIf { it >= 0 } ?: return null

    val type = declaration.initializer?.expressionType
        ?: (declaration.parent as? KtParameter)?.symbol?.returnType
        ?: return null
    val classSymbol = (type as? KaClassType)?.expandedSymbol ?: return null
    if (classSymbol is KaNamedClassSymbol && classSymbol.isData) {
        val primaryConstructor = classSymbol.declaredMemberScope.constructors.firstOrNull { it.isPrimary } ?: return null
        return primaryConstructor.valueParameters.getOrNull(entryIndex)?.name?.asString()
    }

    val mapEntrySymbol = findClass(StandardClassIds.MapEntry) ?: return null
    if (classSymbol != mapEntrySymbol && !classSymbol.isSubClassOf(mapEntrySymbol)) return null

    return listOf("key", "value").getOrNull(entryIndex)
}

@OptIn(KaExperimentalApi::class)
private fun KaSession.lambdaParameterName(parameter: KtParameter): String? {
    val functionLiteral = parameter.ownerFunction as? KtFunctionLiteral ?: return null
    val parameterIndex = functionLiteral.valueParameters.indexOf(parameter).takeIf { it >= 0 } ?: return null
    val lambdaArgument = functionLiteral.getParentOfType<KtLambdaArgument>(strict = true) ?: return null
    val callExpression = lambdaArgument.getParentOfType<KtCallExpression>(strict = true) ?: return null
    val functionType = callExpression.resolveToCall()
        ?.successfulFunctionCallOrNull()
        ?.valueArgumentMapping
        ?.get(lambdaArgument.getArgumentExpression())
        ?.returnType as? KaFunctionType ?: return null

    val valueParameter = functionType.parameters.getOrNull(parameterIndex) ?: return null
    return valueParameter.name?.asString() ?: KotlinNameSuggester().suggestTypeNames(parameter.symbol.returnType).firstOrNull()
}