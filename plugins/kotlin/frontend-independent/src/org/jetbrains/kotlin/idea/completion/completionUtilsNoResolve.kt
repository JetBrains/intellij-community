// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.completion.handlers.WithTailInsertHandler
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.renderer.render
import java.util.ArrayList

val PsiElement.isInsideKtTypeReference: Boolean
    get() = getNonStrictParentOfType<KtTypeReference>() != null

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
        element.withInsertHandler(WithTailInsertHandler.SPACE.asPostInsertHandler)
    } else {
        element
    }
}

fun Name?.labelNameToTail(): String = if (this != null) "@" + render() else ""


