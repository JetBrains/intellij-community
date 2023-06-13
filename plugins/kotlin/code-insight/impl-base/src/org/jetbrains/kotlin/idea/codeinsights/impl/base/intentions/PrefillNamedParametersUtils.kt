// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions

import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgumentList

object PrefillNamedParametersUtils {
    fun prefillName(element: KtValueArgumentList, mandatoryParametersList: List<Name>) {
        val psiFactory = KtPsiFactory(element)
        for (name in mandatoryParametersList) {
            val argumentExpression = psiFactory.createArgument(psiFactory.createExpression("TODO()"), name)
            val comma = psiFactory.createComma()
            val line = psiFactory.createNewLine()
            element.addBefore(argumentExpression, element.lastChild)
            element.addBefore(comma, element.lastChild)
            element.addBefore(line, element.lastChild)
        }
    }

    fun findParameters(element: KtValueArgumentList): List<KtParameter> {
        val parent = element.parent as KtCallElement
        val resolvedReference = parent.calleeExpression?.mainReference?.resolve() ?: return emptyList()
        return if (resolvedReference is KtFunction) {
            resolvedReference.valueParameters
        } else {
             emptyList()
        }
    }
}