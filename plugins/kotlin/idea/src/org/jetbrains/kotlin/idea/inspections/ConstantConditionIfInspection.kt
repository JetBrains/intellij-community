// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.util.hasNoSideEffects
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal fun KtExpression.replaceWithBranchAndMoveCaret(branch: KtExpression, isUsedAsExpression: Boolean, keepBraces: Boolean = false) {
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

    if (replaced != null) {
        caretModel?.moveToOffset(replaced.startOffset)
    }
}
