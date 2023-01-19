// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityRanges
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityTarget
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

object ApplicabilityRanges {
    val SELF = applicabilityTarget<PsiElement> { it }

    val CALLABLE_RETURN_TYPE = applicabilityTarget<KtCallableDeclaration> { decalration ->
        decalration.typeReference?.typeElement
    }

    val VISIBILITY_MODIFIER = modifier(KtTokens.VISIBILITY_MODIFIERS)
    val MODALITY_MODIFIER = modifier(KtTokens.MODALITY_MODIFIERS)

    private fun modifier(tokens: TokenSet) = applicabilityTarget<KtModifierListOwner> { declaration ->
        declaration.modifierList?.getModifier(tokens)
    }

    val CALL_EXCLUDING_LAMBDA_ARGUMENT = applicabilityRanges<KtCallElement> { element ->
        val endElement = element.valueArgumentList ?: element.calleeExpression ?: return@applicabilityRanges emptyList()
        listOf(TextRange(0, endElement.endOffset - element.startOffset))
    }

    val VALUE_ARGUMENT_EXCLUDING_LAMBDA = applicabilityRanges<KtValueArgument> { element ->
        val expression = element.getArgumentExpression() ?: return@applicabilityRanges emptyList()
        if (expression is KtLambdaExpression) {
            // Use OUTSIDE of curly braces only as applicability ranges for lambda.
            // If we use the text range of the curly brace elements, it will include the inside of the braces.
            // This matches FE 1.0 behavior (see AddNameToArgumentIntention).
            listOfNotNull(TextRange(0, 0), TextRange(element.textLength, element.textLength))
        } else {
            listOf(TextRange(0, element.textLength))
        }
    }

    val DECLARATION_WITHOUT_INITIALIZER = applicabilityRange<KtCallableDeclaration> {
        val selfRange = TextRange(0, it.textLength)
        if (it !is KtDeclarationWithInitializer) return@applicabilityRange selfRange
        val initializer = it.initializer ?: return@applicabilityRange selfRange
        // The IDE seems to treat the end offset inclusively when checking if the caret is within the range. Hence we do minus one here
        // so that the intention is available from the following highlighted range.
        //   val i = 1
        //   ^^^^^^^^
        TextRange(0, initializer.startOffsetInParent - 1)
    }

    val DECLARATION_NAME = applicabilityTarget<KtNamedDeclaration> {  element ->
        element.nameIdentifier
    }
}