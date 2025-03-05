// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.psi.PsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.psi.*

@ApiStatus.Internal
object ConstantConditionIfUtils {
    
    fun KtIfExpression.getConditionConstantValueIfAny(): Boolean? {
        var expr = condition
        while (expr is KtParenthesizedExpression) {
            expr = expr.expression
        }
        if (expr !is KtConstantExpression) return null

        analyze(expr) { 
            val constantValue = expr.evaluate()
        
            return (constantValue as? KaConstantValue.BooleanValue)?.value 
        }
    }
    
    fun KtExpression.replaceWithBranch(branch: KtExpression, isUsedAsExpression: Boolean, keepBraces: Boolean = false): PsiElement? {
        // TODO This function uses both AA and modifies PSI. This is error-prone and probably should be refactored
        // TODO Similar code is located in WhenWithOnlyElseInspection 
        
        val subjectVariable = (this as? KtWhenExpression)?.subjectVariable?.let(fun(property: KtProperty): KtProperty? {
            if (property.annotationEntries.isNotEmpty()) return property
            val initializer = property.initializer ?: return property
            val references = ReferencesSearch.search(property, LocalSearchScope(this)).asIterable().toList()
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

        val psiFactory = KtPsiFactory(project)

        val parent = this.parent
        val replaced = when {
            branch !is KtBlockExpression -> {
                if (subjectVariable != null) {
                    replaced(psiFactory.createExpressionByPattern("run { $0\n$1 }", subjectVariable, branch))
                } else {
                    replaced(branch)
                }
            }
            isUsedAsExpression -> {
                if (subjectVariable != null) {
                    branch.addAfter(psiFactory.createNewLine(), branch.addBefore(subjectVariable, branch.statements.firstOrNull()))
                }
                replaced(psiFactory.createExpressionByPattern("run $0", branch.text))
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
                            parent.addAfter(psiFactory.createExpression("run ${branch.text}"), this)
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

        return replaced
    }

    // TODO Similar code is located in WhenWithOnlyElseInspection
    private fun KtExpression.hasNoSideEffects(): Boolean = when (this) {
        is KtStringTemplateExpression -> !hasInterpolation()
        is KtConstantExpression -> true
        else -> analyze(this) { evaluate() != null }
    }
}