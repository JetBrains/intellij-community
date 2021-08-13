// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.completion.handlers.WithTailInsertHandler
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.renderer.render

fun createKeywordElement(
    keyword: String,
    tail: String = "",
    lookupObject: KeywordLookupObject = KeywordLookupObject()
): LookupElementBuilder {
    var element = LookupElementBuilder.create(lookupObject, keyword + tail)
    element = element.withPresentableText(keyword)
    element = element.withBoldness(true)
    if (tail.isNotEmpty()) {
        element = element.withTailText(tail, false)
    }
    return element
}

fun breakOrContinueExpressionItems(position: KtElement, breakOrContinue: String): Collection<LookupElement> {
    val result = ArrayList<LookupElement>()

    parentsLoop@
    for (parent in position.parentsWithSelf) {
        when (parent) {
            is KtLoopExpression -> {
                if (result.isEmpty()) {
                    result.add(createKeywordElement(breakOrContinue))
                }

                val label = (parent.parent as? KtLabeledExpression)?.getLabelNameAsName()
                if (label != null) {
                    result.add(createKeywordElement(breakOrContinue, tail = label.labelNameToTail()))
                }
            }

            is KtDeclarationWithBody -> break@parentsLoop //TODO: support non-local break's&continue's when they are supported by compiler
        }
    }
    return result
}

fun createKeywordElementWithSpace(
    keyword: String,
    tail: String = "",
    addSpaceAfter: Boolean = false,
    lookupObject: KeywordLookupObject = KeywordLookupObject()
): LookupElement {
    val element = createKeywordElement(keyword, tail, lookupObject)
    return if (addSpaceAfter) {
        object : LookupElementDecorator<LookupElement>(element) {
            override fun handleInsert(context: InsertionContext) {
                WithTailInsertHandler.SPACE.handleInsert(context, delegate)
            }
        }
    } else {
        element
    }
}

fun Name?.labelNameToTail(): String = if (this != null) "@" + render() else ""


/**
 *  Checks whether user likely expects completion at the given position to offer a return statement.
 */
fun isLikelyInPositionForReturn(position: KtElement, parent: KtDeclarationWithBody, parentReturnUnit: Boolean): Boolean {
    val isInTopLevelInUnitFunction = parentReturnUnit && position.parent?.parent === parent

    val isInsideLambda = position.getNonStrictParentOfType<KtFunctionLiteral>()?.let { parent.isAncestor(it) } == true
    return when {
        isInsideLambda -> false // for now we do not want to alter completion inside lambda bodies
        position.inReturnExpression() -> false
        position.isRightOperandInElvis() -> true
        position.isLastOrSingleStatement() && !position.isDirectlyInLoopBody() && !isInTopLevelInUnitFunction -> true
        else -> false
    }
}

private fun KtElement.inReturnExpression(): Boolean = findReturnExpression(this) != null

fun KtElement.isRightOperandInElvis(): Boolean {
    val elvisParent = parent as? KtBinaryExpression ?: return false
    return elvisParent.operationToken == KtTokens.ELVIS && elvisParent.right === this
}

/**
 * Checks if expression is either last expression in a block, or a single expression in position where single
 * expressions are allowed (`when` entries, `for` and `while` loops, and `if`s).
 */
private fun PsiElement.isLastOrSingleStatement(): Boolean =
    when (val containingExpression = parent) {
        is KtBlockExpression -> containingExpression.statements.lastOrNull() === this
        is KtWhenEntry, is KtContainerNodeForControlStructureBody -> true
        else -> false
    }

private fun KtElement.isDirectlyInLoopBody(): Boolean {
    val loopContainer = when (val blockOrContainer = parent) {
        is KtBlockExpression -> blockOrContainer.parent as? KtContainerNodeForControlStructureBody
        is KtContainerNodeForControlStructureBody -> blockOrContainer
        else -> null
    }

    return loopContainer?.parent is KtLoopExpression
}

/**
 * If [expression] is directly relates to the return expression already, this return expression will be found.
 *
 * Examples:
 *
 * ```kotlin
 * return 10                                        // 10 is in return
 * return if (true) 10 else 20                      // 10 and 20 are in return
 * return 10 ?: 20                                  // 10 and 20 are in return
 * return when { true -> 10 ; else -> { 20; 30 } }  // 10 and 30 are in return, but 20 is not
 * ```
 */
private tailrec fun findReturnExpression(expression: PsiElement?): KtReturnExpression? =
    when (val parent = expression?.parent) {
        is KtReturnExpression -> parent
        is KtBinaryExpression -> if (parent.operationToken == KtTokens.ELVIS) findReturnExpression(parent) else null
        is KtContainerNodeForControlStructureBody, is KtIfExpression -> findReturnExpression(parent)
        is KtBlockExpression -> if (expression.isLastOrSingleStatement()) findReturnExpression(parent) else null
        is KtWhenEntry -> findReturnExpression(parent.parent)
        else -> null
    }

