// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.util.descendants
import com.intellij.psi.util.parents
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableIntention
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityRange
import org.jetbrains.kotlin.idea.formatter.kotlinCommonSettings
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.util.takeWhileIsInstance

internal class PutCallsOnSeparateLinesIntention :
    AbstractKotlinApplicableIntention<KtQualifiedExpression>(KtQualifiedExpression::class) {
    override fun getActionName(element: KtQualifiedExpression): String = familyName
    override fun getFamilyName(): String = KotlinBundle.message("put.calls.on.separate.lines")

    override fun apply(element: KtQualifiedExpression, project: Project, editor: Editor?) {
        val rootQualifierExpression = element.topmostQualifierExpression() ?: return
        PostprocessReformattingAspect.getInstance(project).disablePostprocessFormattingInside {
            val psiFactory = KtPsiFactory(project)
            rootQualifierExpression.visitOperations(transformation = callChainTransformation(element)) { qualifierExpression ->
                val operationReference = qualifierExpression.operationTokenNode as? PsiElement ?: return@visitOperations
                val whiteSpace = operationReference.prevSibling as? PsiWhiteSpace
                when {
                    whiteSpace == null -> qualifierExpression.addBefore(psiFactory.createNewLine(), operationReference)
                    !whiteSpace.textContains('\n') -> whiteSpace.replace(psiFactory.createWhiteSpace("\n${whiteSpace.text}"))
                }
            }
        }

        CodeStyleManager.getInstance(project).reformat(/* element = */ rootQualifierExpression, /* canChangeWhiteSpacesOnly = */ true)
    }

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtQualifiedExpression> = applicabilityRange {
        (it.operationTokenNode as? PsiElement)?.textRangeInParent
    }

    override fun isApplicableByPsi(element: KtQualifiedExpression): Boolean {
        val topmostQualifierExpression = element.topmostQualifierExpression() ?: return false
        if (topmostQualifierExpression.parent is KtImportDirective) return false

        topmostQualifierExpression.visitOperations(transformation = callChainTransformation(element)) {
            val nextSibling = it.operationTokenNode.treePrev as? PsiWhiteSpace ?: return true
            if (!nextSibling.textContains('\n')) return true
        }

        return false
    }

    private fun callChainTransformation(element: PsiElement): Sequence<KtQualifiedExpression>.() -> Sequence<KtQualifiedExpression> {
        val wrapFirstCall = CodeStyle.getSettings(element.containingFile).kotlinCommonSettings.WRAP_FIRST_METHOD_IN_CALL_CHAIN
        return { if (wrapFirstCall) this else drop(1) }
    }
}

private inline fun KtQualifiedExpression.visitOperations(
    transformation: Sequence<KtQualifiedExpression>.() -> Sequence<KtQualifiedExpression>,
    action: (KtQualifiedExpression) -> Unit,
) {
    descendants(childrenFirst = true) { it is KtQualifiedExpression }
        .filterIsInstance<KtQualifiedExpression>()
        .transformation()
        .forEach(action)
}

private fun KtQualifiedExpression.topmostQualifierExpression(): KtQualifiedExpression? =
    parents(withSelf = true).takeWhileIsInstance<KtQualifiedExpression>().lastOrNull()
