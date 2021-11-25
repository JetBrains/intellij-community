// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.core.replaced
import org.jetbrains.kotlin.idea.inspections.findExistingEditor
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.isElseIf
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.unwrapBlockOrParenthesis
import org.jetbrains.kotlin.idea.util.hasNoSideEffects
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsExpression
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class SimplifyIfExpressionFix(
    element: KtIfExpression,
    private val conditionValue: Boolean,
    private val isUsedAsExpression: Boolean
): KotlinQuickFixAction<KtIfExpression>(element) {
    override fun getFamilyName() =
        if (canRemove(element, conditionValue)) KotlinBundle.message("remove.fix.text") else KotlinBundle.message("simplify.fix.text")

    override fun getText(): String = familyName

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val ifExpression = element ?: return
        simplifyIfPossible(ifExpression, conditionValue, isUsedAsExpression)
    }

    companion object {
        fun createFix(expression: KtIfExpression, conditionValue: Boolean): SimplifyIfExpressionFix? {
            return when {
                canRemove(expression, conditionValue) ->
                    SimplifyIfExpressionFix(expression, conditionValue, isUsedAsExpression = false)
                expression.branch(conditionValue) != null -> {
                    val isUsedAsExpression = expression.isUsedAsExpression(expression.analyze(BodyResolveMode.PARTIAL_WITH_CFA))
                    SimplifyIfExpressionFix(expression, conditionValue, isUsedAsExpression)
                }
                else -> null
            }
        }

        fun simplifyIfPossible(
            expression: KtIfExpression,
            conditionValue: Boolean,
            isUsedAsExpression: Boolean = expression.isUsedAsExpression(expression.analyze(BodyResolveMode.PARTIAL_WITH_CFA))
        ) {
            if (canRemove(expression, conditionValue)) {
                val parent = expression.parent
                if (parent.node.elementType == KtNodeTypes.ELSE) {
                    (parent.parent as? KtIfExpression)?.elseKeyword?.delete()
                }
                expression.delete()
                return
            }

            val branch = expression.branch(conditionValue)
            if (branch != null) {
                val keepBraces = expression.isElseIf() && branch is KtBlockExpression
                val newBranch = if (keepBraces) branch else branch.unwrapBlockOrParenthesis()
                expression.replaceWithBranch(newBranch, isUsedAsExpression, keepBraces)
            }
        }

        private fun canRemove(expression: KtIfExpression?, conditionValue: Boolean) =
            expression != null && !conditionValue && expression.`else` == null

        fun KtIfExpression.getConditionConstantValueIfAny(): Boolean? {
            var expr = condition
            while (expr is KtParenthesizedExpression) {
                expr = expr.expression
            }
            if (expr !is KtConstantExpression) return null
            val context = condition?.analyze(BodyResolveMode.PARTIAL_WITH_CFA) ?: return null
            val type = expr.getType(context) ?: return null
            val constant = ConstantExpressionEvaluator.getConstant(expr, context)?.toConstantValue(type) ?: return null
            return constant.value as? Boolean
        }


        private fun KtIfExpression.branch(thenBranch: Boolean) = if (thenBranch) then else `else`
    }
}

fun KtExpression.replaceWithBranch(branch: KtExpression, isUsedAsExpression: Boolean, keepBraces: Boolean = false) {
    val caretModel = findExistingEditor()?.caretModel

    val subjectVariable = (this as? KtWhenExpression)?.subjectVariable?.let(fun(property: KtProperty): KtProperty? {
        if (property.annotationEntries.isNotEmpty()) return property
        val initializer = property.initializer ?: return property
        val references = ReferencesSearch.search(property, LocalSearchScope(this)).toList()
        return when (references.size) {
            0 -> property.takeUnless { initializer.hasNoSideEffects() }
            1 -> {
                if (initializer.hasNoSideEffects()) {
                    references.first().element.replace(initializer)
                    null
                } else
                    property
            }
            else -> property
        }
    })

    val factory = KtPsiFactory(this)
    val parent = this.parent
    val replaced = when {
        branch !is KtBlockExpression -> {
            if (subjectVariable != null) {
                replaced(KtPsiFactory(this).createExpressionByPattern("run { $0\n$1 }", subjectVariable, branch))
            } else {
                replaced(branch)
            }
        }
        isUsedAsExpression -> {
            if (subjectVariable != null) {
                branch.addAfter(factory.createNewLine(), branch.addBefore(subjectVariable, branch.statements.firstOrNull()))
            }
            replaced(factory.createExpressionByPattern("run $0", branch.text))
        }
        else -> {
            val firstChildSibling = branch.firstChild.nextSibling
            val lastChild = branch.lastChild
            val replaced = if (firstChildSibling != lastChild) {
                if (keepBraces) {
                    parent.addAfter(branch, this)
                } else {
                    if (subjectVariable != null) {
                        branch.addAfter(subjectVariable, branch.lBrace)
                        parent.addAfter(KtPsiFactory(this).createExpression("run ${branch.text}"), this)
                    } else {
                        parent.addRangeAfter(firstChildSibling, lastChild.prevSibling, this)
                    }
                }
            } else {
                null
            }
            delete()
            replaced
        }
    }

    if (replaced != null) {
        caretModel?.moveToOffset(replaced.startOffset)
    }
}
