// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.resolution.singleConstructorCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaParameterSymbol
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.AddConstructorParameterFromSuperTypeCallFix
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.types.Variance
import kotlin.collections.indexOf

internal object AddConstructorParameterFromSuperTypeCallFixFactory {
    @OptIn(KaExperimentalApi::class)
    private fun KaSession.createCallFix(diagnostic: KaFirDiagnostic.NoValueForParameter): AddConstructorParameterFromSuperTypeCallFix? {
        val superTypeCallEntry = diagnostic.psi as? KtSuperTypeCallEntry ?: return null
        val valueArgumentList = superTypeCallEntry.valueArgumentList ?: return null
        val parameterSymbol = diagnostic.violatedParameter as? KaParameterSymbol ?: return null
        val containingSymbol = parameterSymbol.containingSymbol as? KaFunctionSymbol ?: return null
        val parameterIndex = containingSymbol.valueParameters.indexOf(parameterSymbol)
        val superTypeCall = superTypeCallEntry.resolveToCall()?.singleConstructorCallOrNull() ?: return null
        val containingClass = superTypeCallEntry.containingClass() ?: return null
        // Enum entries are classes but cannot have parameters added to their constructors.
        if (containingClass is KtEnumEntry) return null

        if (parameterIndex != valueArgumentList.arguments.size) return null
        val primaryConstructor = containingClass.primaryConstructor
        if (primaryConstructor?.valueParameters?.any { it.name == parameterSymbol.name.toString() } == true) return null
        val superTypeCallParameters = superTypeCall.partiallyAppliedSymbol.signature.valueParameters
        val parameterType = superTypeCallParameters.getOrNull(parameterIndex)?.returnType ?: return null

        val renderedParameterType = parameterType.render(position = Variance.INVARIANT)
        val renderedName = parameterSymbol.name.render()

        return AddConstructorParameterFromSuperTypeCallFix(valueArgumentList, renderedName, renderedParameterType)
    }

    val noValueForParameter = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NoValueForParameter ->
        listOfNotNull(createCallFix(diagnostic))
    }
}