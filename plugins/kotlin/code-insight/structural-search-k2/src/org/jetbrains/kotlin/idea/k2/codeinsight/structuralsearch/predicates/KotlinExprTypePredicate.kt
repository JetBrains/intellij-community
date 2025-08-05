// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.predicates

import com.intellij.psi.PsiElement
import com.intellij.structuralsearch.StructuralSearchUtil
import com.intellij.structuralsearch.impl.matcher.MatchContext
import com.intellij.structuralsearch.impl.matcher.predicates.MatchPredicate
import com.intellij.structuralsearch.impl.matcher.predicates.RegExpPredicate
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.allSupertypes
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.renderNames
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isNull

internal class KotlinExprTypePredicate(
    private val search: String,
    private val withinHierarchy: Boolean,
    private val ignoreCase: Boolean,
    private val target: Boolean,
    private val baseName: String,
    private val regex: Boolean
) : MatchPredicate() {
    override fun match(matchedNode: PsiElement, start: Int, end: Int, context: MatchContext): Boolean {
        val node = StructuralSearchUtil.getParentIfIdentifier(matchedNode)
        if (node !is KtElement) return false
        analyze(node) {
            val type = when {
                node is KtClassLikeDeclaration -> (node.mainReference?.resolveToSymbol() as? KaNamedClassSymbol)?.defaultType
                node is KtCallableDeclaration -> node.returnType
                node is KtExpression -> {
                    // because `getKtType` will return void for enum references we resolve and build type from the resolved class when
                    // possible.
                    val symbol = node.mainReference?.resolveToSymbol()
                    if (symbol is KaNamedClassSymbol) {
                        symbol.defaultType
                    } else {
                        node.expressionType
                    }

                }
                node is KtStringTemplateEntry && node !is KtSimpleNameStringTemplateEntry -> null
                node is KtSimpleNameStringTemplateEntry -> node.expression?.expressionType
                else -> null
            } ?: return false
            val searchedTypeNames = if (regex) listOf() else search.split('|')
            if (matchedNode is KtExpression && matchedNode.isNull() && searchedTypeNames.contains("null")) return true
            return match(type)
        }
    }

    context(_: KaSession)
    fun match(type: KaType): Boolean {
        val typesToTest = mutableListOf(type)
        if (withinHierarchy) typesToTest.addAll(type.allSupertypes)
        if (regex) {
            val delegate = RegExpPredicate(search, !ignoreCase, baseName, false, target)
            return typesToTest.any { type.renderNames().any { delegate.match(it) } }
        } else {
            val searchedTypeNames = search.split('|')
            return searchedTypeNames.any { templateTypeName ->
                typesToTest.any { typeToTest ->
                    typeToTest.renderNames().any {
                        templateTypeName == it
                    }
                }
            }
        }
    }
}