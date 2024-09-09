// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.descendantsOfType
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.InlineTypeParameterFix
import org.jetbrains.kotlin.idea.quickfix.prepareInlinedTypeParameterContext
import org.jetbrains.kotlin.psi.KtTypeParameterListOwner
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal object FinalUpperBoundFixFactories {

    val inlineTypeParameterFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.FinalUpperBound ->
        val element = diagnostic.psi as? KtTypeReference ?: return@ModCommandBased emptyList()
        val parameterListOwner = element.getStrictParentOfType<KtTypeParameterListOwner>() ?: return@ModCommandBased emptyList()
        val parameterList = parameterListOwner.typeParameterList ?: return@ModCommandBased emptyList()
        val (parameter, _, _) = prepareInlinedTypeParameterContext(element, parameterList) ?: return@ModCommandBased emptyList()
        val parameterSymbol = parameter.symbol

        val typeReferencesToInline = parameterListOwner
            .descendantsOfType<KtTypeReference>()
            .filter { typeReference ->
                val typeElement = typeReference.typeElement
                val type = typeReference.type as? KaTypeParameterType
                typeElement != null && type?.symbol == parameterSymbol
            }.map { it.createSmartPointer() }
            .toList()

        listOf(
            InlineTypeParameterFix(element, typeReferencesToInline)
        )
    }
}
