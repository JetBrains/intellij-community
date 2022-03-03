// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.elementType
import com.intellij.psi.util.nextLeafs
import com.intellij.psi.util.siblings
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.core.replaced
import org.jetbrains.kotlin.idea.util.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class ConvertConcatenationToBuildStringIntention : SelfTargetingIntention<KtBinaryExpression>(
    KtBinaryExpression::class.java,
    KotlinBundle.lazyMessage("convert.concatenation.to.build.string")
) {

    override fun isApplicableTo(element: KtBinaryExpression, caretOffset: Int): Boolean {
        return element.isConcatenation() && !element.parent.isConcatenation() && !element.mustBeConstant()
    }

    override fun applyTo(element: KtBinaryExpression, editor: Editor?) {
        val buildStringCall = KtPsiFactory(element).buildExpression {
            appendFixedText("kotlin.text.buildString {\n")
            element.allExpressions().forEach { expression ->
                appendFixedText("append(")
                if (expression is KtStringTemplateExpression) {
                    val singleEntry = expression.entries.singleOrNull()
                    if (singleEntry is KtStringTemplateEntryWithExpression) {
                        appendExpression(singleEntry.expression)
                    } else {
                        appendNonFormattedText(expression.text)
                    }
                } else {
                    appendExpression(expression)
                }
                appendFixedText(")")
                val tailComments = expression.tailComments()
                if (tailComments.isNotEmpty()) {
                    appendFixedText(" ")
                    tailComments.forEach { appendNonFormattedText(it.text) }
                }
                appendFixedText("\n")
            }
            appendFixedText("}")
        }
        element.deleteTailComments()

        val replaced = element.replaced(buildStringCall)
        ShortenReferences.DEFAULT.process(replaced)
    }

    private fun PsiElement.isConcatenation(): Boolean {
        if (this !is KtBinaryExpression) return false
        if (operationToken != KtTokens.PLUS) return false
        val type = getType(safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL)) ?: return false
        return KotlinBuiltIns.isString(type)
    }

    private fun KtBinaryExpression.allExpressions(): List<KtExpression> {
        val expressions = mutableListOf<KtExpression>()
        fun collect(expression: KtExpression?) {
            when (expression) {
                is KtBinaryExpression -> {
                    collect(expression.left)
                    collect(expression.right)
                }
                is KtExpression ->
                    expressions.add(expression)
            }
        }
        collect(this)
        return expressions
    }

    private fun KtExpression.tailComments(): List<PsiElement> {
        val tailElements = this.nextLeafs
            .takeWhile { it is PsiWhiteSpace || it is PsiComment || it.elementType == KtTokens.PLUS }
            .dropWhile { it !is PsiComment }
        return if (tailElements.any { it is PsiComment }) {
            tailElements.toList().dropLastWhile { it is PsiWhiteSpace }
        } else {
            emptyList()
        }
    }

    private fun KtExpression.deleteTailComments() {
        siblings(withSelf = false)
            .takeWhile { !it.isWhiteSpaceWithLineBreak() }
            .filter { it is PsiComment }
            .forEach { it.delete() }
    }

    private fun PsiElement.isWhiteSpaceWithLineBreak() = this is PsiWhiteSpace && this.textContains('\n')
}

internal fun KtExpression.mustBeConstant(): Boolean = this.parents.any { it is KtAnnotationEntry }
