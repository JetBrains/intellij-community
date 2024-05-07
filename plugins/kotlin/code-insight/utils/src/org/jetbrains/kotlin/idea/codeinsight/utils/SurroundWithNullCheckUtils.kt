// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getLastParentOfTypeInRow
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.util.match

object SurroundWithNullCheckUtils {

    fun applyTo(
        project: Project,
        element: KtExpression,
        nullableExpression: KtExpression,
    ) {
        val psiFactory = KtPsiFactory(project)
        val surrounded = psiFactory.createExpressionByPattern("if ($0 != null) { $1 }", nullableExpression, element)
        element.replace(surrounded)
    }

    fun KtExpression.hasAcceptableParent() = with(parent) {
        this is KtBlockExpression || this.parent is KtIfExpression || this is KtWhenEntry || this.parent is KtLoopExpression
    }

    fun getForExpressionIfApplicable(nullableExpression: KtReferenceExpression): KtForExpression? {
        val forExpression = nullableExpression.parents.match(KtContainerNode::class, last = KtForExpression::class) ?: return null
        return if (forExpression.parent is KtBlockExpression) forExpression
        else null
    }

    fun getRootExpressionIfApplicable(nullableExpression: KtReferenceExpression): KtExpression? {
        val root = when (val parent = nullableExpression.parent) {
            is KtValueArgument -> {
                val call = parent.getParentOfType<KtCallExpression>(true) ?: return null
                call.getLastParentOfTypeInRow<KtQualifiedExpression>() ?: call
            }

            is KtBinaryExpression -> {
                if (parent.right != nullableExpression) return null
                parent
            }

            else -> return null
        }
        return if (root.parent is KtBlockExpression) return root
        else null
    }

    fun getNullableExpressionIfApplicable(element: PsiElement): KtReferenceExpression? {
        val parent = element.parent
        return when (parent) {
            is KtDotQualifiedExpression -> parent.receiverExpression
            is KtBinaryExpression -> if (parent.operationToken == KtTokens.IN_KEYWORD) parent.right else parent.left
            is KtCallExpression -> parent.calleeExpression
            else -> null
        } as? KtReferenceExpression
    }
}