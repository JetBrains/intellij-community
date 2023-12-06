// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.descendants
import com.intellij.psi.util.parents
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableModCommandIntention
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.util.takeWhileIsInstance

internal class PutExpressionsOnSeparateLinesIntention :
    AbstractKotlinApplicableModCommandIntention<KtOperationReferenceExpression>(KtOperationReferenceExpression::class) {

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtOperationReferenceExpression> =
        ApplicabilityRanges.SELF

    override fun isApplicableByPsi(element: KtOperationReferenceExpression): Boolean {
        element.topmostBinaryExpression()?.visitOperations {
            val nextSibling = it.nextSibling as? PsiWhiteSpace ?: return true
            if (!nextSibling.textContains('\n')) return true
        }

        return false
    }

    override fun getActionName(element: KtOperationReferenceExpression): String = familyName
    override fun getFamilyName(): String = KotlinBundle.message("put.expressions.on.separate.lines")

    override fun invoke(context: ActionContext, element: KtOperationReferenceExpression, updater: ModPsiUpdater) {
        val rootBinaryExpression = element.topmostBinaryExpression() ?: return
        val project = context.project
        val psiFactory = KtPsiFactory(project)
        rootBinaryExpression.visitOperations { operationReference ->
            val whiteSpace = operationReference.nextSibling as? PsiWhiteSpace
            when {
                whiteSpace == null -> operationReference.parent.addAfter(psiFactory.createNewLine(), operationReference)
                !whiteSpace.textContains('\n') -> whiteSpace.replace(psiFactory.createWhiteSpace("\n${whiteSpace.text}"))
            }
        }

        CodeStyleManager.getInstance(project).reformat(/* element = */ rootBinaryExpression, /* canChangeWhiteSpacesOnly = */ true)
    }
}

private inline fun KtBinaryExpression.visitOperations(action: (KtOperationReferenceExpression) -> Unit) {
    descendants(childrenFirst = true) { it is KtBinaryExpression && it.hasOrOrOrAndAnd() }
        .filterIsInstance<KtOperationReferenceExpression>()
        .forEach(action)
}

private fun KtOperationReferenceExpression.topmostBinaryExpression(): KtBinaryExpression? =
    parents(withSelf = false).takeWhileIsInstance<KtBinaryExpression>().takeWhile { it.hasOrOrOrAndAnd() }.lastOrNull()

private fun KtBinaryExpression.hasOrOrOrAndAnd(): Boolean {
    val signToken = operationToken
    return signToken == KtTokens.OROR || signToken == KtTokens.ANDAND
}
