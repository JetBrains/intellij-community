// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.addRemoveModifier.addModifier
import org.jetbrains.kotlin.types.Variance

/**
 * Analyzes type parameter usage within its containing class to suggest appropriate variance modifiers.
 */
internal class AddVarianceModifierInspection : KotlinApplicableInspectionBase.Simple<KtTypeParameter, Variance>() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : KtVisitorVoid() {
        override fun visitTypeParameter(parameter: KtTypeParameter) {
            visitTargetElement(parameter, holder, isOnTheFly)
        }
    }

    override fun isApplicableByPsi(element: KtTypeParameter): Boolean {
        return element.variance == Variance.INVARIANT
    }

    override fun KaSession.prepareContext(element: KtTypeParameter): Variance? {
        val classSymbol = element.symbol.containingSymbol as? KaNamedClassSymbol ?: return null
        val typeParameterSymbol = element.symbol
        
        var usedInContravariantPosition = false
        var usedInCovariantPosition = false
        
        analyzeConstructorUsage(classSymbol, typeParameterSymbol) { usedInContravariantPosition = true }
        analyzeMemberUsage(classSymbol, typeParameterSymbol) { contravariant, covariant ->
            usedInContravariantPosition = usedInContravariantPosition || contravariant
            usedInCovariantPosition = usedInCovariantPosition || covariant
        }
        
        return determineVarianceSuggestion(usedInContravariantPosition, usedInCovariantPosition)
    }
    
    private fun KaSession.analyzeConstructorUsage(
        classSymbol: KaNamedClassSymbol,
        typeParameterSymbol: KaTypeParameterSymbol,
        onContravariantUsage: () -> Unit
    ) {
        val primaryConstructor = classSymbol.declaredMemberScope.constructors.firstOrNull { it.isPrimary }
        primaryConstructor?.valueParameters?.forEach { param ->
            if (typeReferencesTypeParameter(typeParameterSymbol, param.returnType)) {
                onContravariantUsage()
            }
        }
    }
    
    private fun KaSession.analyzeMemberUsage(
        classSymbol: KaNamedClassSymbol,
        typeParameterSymbol: KaTypeParameterSymbol,
        onUsage: (contravariant: Boolean, covariant: Boolean) -> Unit
    ) {
        classSymbol.declaredMemberScope.callables.forEach { callable ->
            when (callable) {
                is KaPropertySymbol -> analyzePropertyUsage(callable, typeParameterSymbol, onUsage)
                is KaFunctionSymbol -> analyzeFunctionUsage(callable, typeParameterSymbol, onUsage)
                else -> { /* Other symbols are not relevant for variance analysis */ }
            }
        }
    }
    
    private fun KaSession.analyzePropertyUsage(
        property: KaPropertySymbol,
        typeParameterSymbol: KaTypeParameterSymbol,
        onUsage: (contravariant: Boolean, covariant: Boolean) -> Unit
    ) {
        if (!isPublicApi(property.visibility)) return
        if (!typeReferencesTypeParameter(typeParameterSymbol, property.returnType)) return
        
        val isMutable = property.setter != null
        onUsage(isMutable, true) // Properties are always covariant, mutable ones are also contravariant
    }
    
    private fun KaSession.analyzeFunctionUsage(
        function: KaFunctionSymbol,
        typeParameterSymbol: KaTypeParameterSymbol,
        onUsage: (contravariant: Boolean, covariant: Boolean) -> Unit
    ) {
        if (!isPublicApi(function.visibility)) return
        
        val usedInReturnType = typeReferencesTypeParameter(typeParameterSymbol, function.returnType)
        val usedInParameters = function.valueParameters.any { param ->
            typeReferencesTypeParameter(typeParameterSymbol, param.returnType)
        }
        
        if (usedInReturnType || usedInParameters) {
            onUsage(usedInParameters, usedInReturnType)
        }
    }
    
    private fun isPublicApi(visibility: KaSymbolVisibility): Boolean =
        visibility == KaSymbolVisibility.PUBLIC || visibility == KaSymbolVisibility.INTERNAL
    
    private fun determineVarianceSuggestion(
        contravariantUsage: Boolean,
        covariantUsage: Boolean
    ): Variance? = when {
        contravariantUsage && covariantUsage -> null // Conflicting usages
        contravariantUsage -> Variance.IN_VARIANCE
        covariantUsage -> Variance.OUT_VARIANCE
        else -> null // No relevant usage found
    }
    
    private fun KaSession.typeReferencesTypeParameter(typeParameter: KaTypeParameterSymbol, type: KaType): Boolean {
        return when (type) {
            is KaTypeParameterType -> type.symbol == typeParameter
            is KaClassType -> type.typeArguments.any { arg ->
                arg.type?.let { typeReferencesTypeParameter(typeParameter, it) } == true
            }
            else -> false
        }
    }

    override fun getProblemDescription(element: KtTypeParameter, context: Variance): String {
        return KotlinBundle.message("type.parameter.can.have.0.variance", context)
    }

    override fun getProblemHighlightType(element: KtTypeParameter, context: Variance): ProblemHighlightType =
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING

    override fun createQuickFix(element: KtTypeParameter, context: Variance): KotlinModCommandQuickFix<KtTypeParameter> =
        AddVarianceFix(context)

    private class AddVarianceFix(val variance: Variance) : KotlinModCommandQuickFix<KtTypeParameter>() {
        override fun getName() = KotlinBundle.message("add.variance.fix.text", variance)
        override fun getFamilyName() = KotlinBundle.message("add.variance.fix.family.name")
        override fun applyFix(project: Project, element: KtTypeParameter, updater: ModPsiUpdater) {
            addModifier(element, if (variance == Variance.IN_VARIANCE) KtTokens.IN_KEYWORD else KtTokens.OUT_KEYWORD)
        }
    }
}
