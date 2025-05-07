// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.implCommon

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.nextLeaf
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration
import org.jetbrains.kotlin.types.expressions.OperatorConventions
import org.jetbrains.kotlin.util.OperatorNameConventions.COMPARE_TO
import org.jetbrains.kotlin.util.OperatorNameConventions.CONTAINS
import org.jetbrains.kotlin.util.OperatorNameConventions.EQUALS
import org.jetbrains.kotlin.util.OperatorNameConventions.GET
import org.jetbrains.kotlin.util.OperatorNameConventions.INVOKE
import org.jetbrains.kotlin.util.OperatorNameConventions.SET

object OperatorNameCompletion {

    private val additionalOperatorPresentation = mapOf(
        SET to "[...] = ...",
        GET to "[...]",
        CONTAINS to "in !in",
        COMPARE_TO to "< > <= >=",
        EQUALS to "== !=",
        INVOKE to "(...)"
    )

    fun isPositionApplicable(
        nameExpression: KtElement?,
        expression: KtElement?,
        position: PsiElement,
        originalFunctionProvider: (KtNamedFunction) -> KtNamedFunction?
    ): Boolean {
        if (nameExpression == null || nameExpression != expression) return false
        val func = position.getParentOfType<KtNamedFunction>(strict = false) ?: return false
        val funcNameIdentifier = func.nameIdentifier ?: return false
        val identifierInNameExpression = nameExpression.nextLeaf {
            it is LeafPsiElement && it.elementType == KtTokens.IDENTIFIER
        } ?: return false

        if (!func.hasModifier(KtTokens.OPERATOR_KEYWORD) || identifierInNameExpression != funcNameIdentifier) return false
        val originalFunction = originalFunctionProvider(func) ?: return false
        return !originalFunction.isTopLevel || originalFunction.isExtensionDeclaration()
    }

    fun decorateLookupElement(lookupElement: LookupElementBuilder, opName: Name): LookupElementBuilder {
        val symbol =
            (OperatorConventions.getOperationSymbolForName(opName) as? KtSingleValueToken)?.value ?: additionalOperatorPresentation[opName]

        if (symbol != null) return lookupElement.withTypeText(symbol)
        return lookupElement
    }

    fun getApplicableOperators(descriptorNameFilter: (String) -> Boolean): List<Name> {
        return OperatorConventions.CONVENTION_NAMES
            .filter { descriptorNameFilter(it.asString()) }
    }
}