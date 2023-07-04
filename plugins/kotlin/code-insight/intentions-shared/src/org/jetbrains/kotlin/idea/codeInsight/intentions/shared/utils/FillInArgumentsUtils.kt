// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared.utils

import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgumentList

internal object FillInArgumentsUtils {

    fun getFunctionArguments(element: KtValueArgumentList) = element.children.map { it.firstChild.text }.toSet()

    fun isApplicableByPsi(element: KtValueArgumentList, isOptional: Boolean): Boolean {
        val arguments = getFunctionArguments(element)
        val callElement = element.parent as KtCallElement
        val resolvedReference = callElement.calleeExpression?.mainReference?.resolve() ?: return false
        val paramsChildren = if (resolvedReference is KtFunction) {
            resolvedReference.valueParameters
        } else {
            return false
        }

        val isMissingMandatoryArguments: Boolean = paramsChildren.any {
            val ktParam = (it as KtParameter)
            val parameterName = ktParam.name ?: return@any false
            !ktParam.hasDefaultValue() && parameterName !in arguments
        }
        if (!isOptional) return isMissingMandatoryArguments
        if (isMissingMandatoryArguments) return false

        return paramsChildren.any {
            val ktParam = (it as KtParameter)
            val parameterName = ktParam.name ?: return@any false
            ktParam.hasDefaultValue() && parameterName !in arguments
        }
    }

    fun fillInArguments(element: KtValueArgumentList, argumentsList: List<Name>) {
        argumentsList.ifEmpty { return }
        val psiFactory = KtPsiFactory(element.project)
        val isNewLine = isNewLine(element, argumentsList.size)
        val comma = psiFactory.createComma()
        var currentChild = element.lastChild.prevSibling
        if (currentChild.elementType != KtTokens.LPAR) {
            while (currentChild.elementType == KtTokens.WHITE_SPACE) {
                currentChild = currentChild.prevSibling
            }
            if (currentChild.elementType != KtTokens.COMMA) {
                element.addBefore(comma, element.lastChild)
            }
        }
        for ((index, name) in argumentsList.withIndex()) {
            val argumentExpression = psiFactory.createArgument(psiFactory.createExpression("TODO()"), name)
            val separator = if (isNewLine) psiFactory.createNewLine() else psiFactory.createWhiteSpace()
            element.addBefore(argumentExpression, element.lastChild)
            if (index != argumentsList.size - 1 || isNewLine) {
                element.addBefore(comma, element.lastChild)
                element.addBefore(separator, element.lastChild)
            }
        }
    }

    private fun isNewLine(element: KtValueArgumentList, numOfNewArguments: Int): Boolean {
        val existingArguments = element.arguments

        if (existingArguments.isNotEmpty() && existingArguments.firstOrNull()?.getLineNumber() != existingArguments.lastOrNull()
                ?.getLineNumber()
        ) {
            // generate on new lines
            return true
        }
        if (existingArguments.isNotEmpty() && existingArguments.firstOrNull()?.getLineNumber() == existingArguments.lastOrNull()
                ?.getLineNumber()
        ) {
            // generate on the same line
            return false
        }
        return existingArguments.size + numOfNewArguments > 3
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