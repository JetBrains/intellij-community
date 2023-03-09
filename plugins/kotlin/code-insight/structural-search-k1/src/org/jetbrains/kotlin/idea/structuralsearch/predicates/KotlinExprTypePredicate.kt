// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.structuralsearch.predicates

import com.intellij.psi.PsiElement
import com.intellij.structuralsearch.StructuralSearchUtil
import com.intellij.structuralsearch.impl.matcher.MatchContext
import com.intellij.structuralsearch.impl.matcher.predicates.MatchPredicate
import com.intellij.structuralsearch.impl.matcher.predicates.RegExpPredicate
import com.intellij.util.asSafely
import org.jetbrains.kotlin.idea.core.resolveType
import org.jetbrains.kotlin.idea.structuralsearch.resolveDeclType
import org.jetbrains.kotlin.idea.structuralsearch.resolveExprType
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isNull
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.supertypes

class KotlinExprTypePredicate(
    private val search: String,
    private val withinHierarchy: Boolean,
    private val ignoreCase: Boolean,
    private val target: Boolean,
    private val baseName: String,
    private val regex: Boolean
) : MatchPredicate() {
    override fun match(matchedNode: PsiElement, start: Int, end: Int, context: MatchContext): Boolean {
        val node = StructuralSearchUtil.getParentIfIdentifier(matchedNode)
        val type = when {
            node is KtDeclaration -> node.resolveDeclType()
            node is KtExpression -> node.resolveExprType() ?: node.parent?.asSafely<KtDotQualifiedExpression>()?.resolveExprType()
            node is KtStringTemplateEntry && node !is KtSimpleNameStringTemplateEntry -> null
            node is KtSimpleNameStringTemplateEntry -> node.expression?.resolveType()
            else -> null
        } ?: return false
        val searchedTypeNames = if (regex) listOf() else search.split('|')
        if (matchedNode is KtExpression && matchedNode.isNull() && searchedTypeNames.contains("null")) return true
        return match(type)
    }

    fun match(type: KotlinType): Boolean {
        val typesToTest = mutableListOf(type)
        if (withinHierarchy) typesToTest.addAll(type.supertypes())
        if (regex) {
            val delegate = RegExpPredicate(search, !ignoreCase, baseName, false, target)
            return typesToTest.any {
                delegate.match(DescriptorRenderer.SHORT_NAMES_IN_TYPES.renderType(it))
                        || delegate.match(DescriptorRenderer.FQ_NAMES_IN_TYPES.renderType(it))
            }
        } else {
            val searchedTypeNames = search.split('|')
            return searchedTypeNames.any { templateTypeName ->
                typesToTest.any { typeToTest ->
                    templateTypeName == DescriptorRenderer.SHORT_NAMES_IN_TYPES.renderType(typeToTest)
                            || templateTypeName == DescriptorRenderer.FQ_NAMES_IN_TYPES.renderType(typeToTest)
                }
            }
        }
    }
}