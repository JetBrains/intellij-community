// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils


import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.codeInsight.template.TextResult
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import org.jetbrains.annotations.Nls


abstract class ChooseValueExpression<in T : Any>(
    lookupItems: Collection<T>,
    defaultItem: T,
    @Nls private val advertisementText: String? = null
) : Expression() {
    protected abstract fun getLookupString(element: T): String
    protected abstract fun getResult(element: T): String

    @Suppress("LeakingThis")
    private val defaultItemString = getLookupString(defaultItem)

    private val lookupItems: Array<LookupElement> = lookupItems.map { suggestion ->
        LookupElementBuilder.create(suggestion, getLookupString(suggestion)).withInsertHandler { context, item ->
            val topLevelEditor = InjectedLanguageUtil.getTopLevelEditor(context.editor)
            TemplateManagerImpl.getTemplateState(topLevelEditor)?.currentVariableRange?.let { range ->
                @Suppress("UNCHECKED_CAST")
                topLevelEditor.document.replaceString(range.startOffset, range.endOffset, getResult(item.`object` as T))
            }
        }
    }.toTypedArray()

    override fun calculateLookupItems(context: ExpressionContext) = if (lookupItems.size > 1) lookupItems else null

    override fun calculateQuickResult(context: ExpressionContext) = calculateResult(context)

    override fun calculateResult(context: ExpressionContext) = TextResult(defaultItemString)

    override fun getAdvertisingText() = advertisementText
}

class ChooseStringExpression(
    suggestions: Collection<String>,
    default: String = suggestions.first(),
    @Nls advertisementText: String? = null
) : ChooseValueExpression<String>(suggestions, default, advertisementText) {
    override fun getLookupString(element: String) = element
    override fun getResult(element: String) = element
}
