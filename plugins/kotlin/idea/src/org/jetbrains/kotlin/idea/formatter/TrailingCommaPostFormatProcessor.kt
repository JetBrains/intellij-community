// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.formatter

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessorHelper
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.util.reformatted
import org.jetbrains.kotlin.idea.codeinsight.utils.trailingCommaAllowedInModule
import org.jetbrains.kotlin.idea.codeinsights.impl.base.visitor.TrailingCommaVisitor
import org.jetbrains.kotlin.idea.formatter.trailingComma.TrailingCommaContext
import org.jetbrains.kotlin.idea.formatter.trailingComma.TrailingCommaHelper.findInvalidCommas
import org.jetbrains.kotlin.idea.formatter.trailingComma.TrailingCommaHelper.lineBreakIsMissing
import org.jetbrains.kotlin.idea.formatter.trailingComma.TrailingCommaHelper.trailingCommaOrLastElement
import org.jetbrains.kotlin.idea.formatter.trailingComma.TrailingCommaState
import org.jetbrains.kotlin.idea.formatter.trailingComma.addTrailingCommaIsAllowedFor
import org.jetbrains.kotlin.idea.util.leafIgnoringWhitespace
import org.jetbrains.kotlin.idea.util.leafIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.psi.psiUtil.siblings

class TrailingCommaPostFormatProcessor : PostFormatProcessor {
    override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement {
        if (source.language != KotlinLanguage.INSTANCE) return source

        return TrailingCommaPostFormatVisitor(settings).process(source)
    }

    override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
        if (source.language != KotlinLanguage.INSTANCE) return rangeToReformat

        return TrailingCommaPostFormatVisitor(settings).processText(source, rangeToReformat)
    }
}

private class TrailingCommaPostFormatVisitor(private val settings: CodeStyleSettings) : TrailingCommaVisitor() {
    private val myPostProcessor = PostFormatProcessorHelper(settings.kotlinCommonSettings)

    override fun process(trailingCommaContext: TrailingCommaContext) = processIfInRange(trailingCommaContext.ktElement) {
        processCommaOwner(trailingCommaContext)
    }

    private fun processIfInRange(element: KtElement, block: () -> Unit = {}) {
        if (myPostProcessor.isElementPartlyInRange(element)) {
            block()
        }
    }

    private fun processCommaOwner(trailingCommaContext: TrailingCommaContext) {
        val ktElement = trailingCommaContext.ktElement

        val lastElementOrComma = trailingCommaOrLastElement(ktElement) ?: return
        updatePsi(ktElement) {
            val state = trailingCommaContext.state
            when {
                state == TrailingCommaState.MISSING && settings.kotlinCustomSettings.addTrailingCommaIsAllowedFor(ktElement) -> {
                    // add a missing comma
                    val hasChange = if (trailingCommaAllowedInModule(ktElement)) {
                        lastElementOrComma.addCommaAfter(KtPsiFactory(ktElement))
                        true
                    } else {
                        false
                    }

                    correctCommaPosition(ktElement) || hasChange
                }

                state == TrailingCommaState.EXISTS -> {
                    correctCommaPosition(ktElement)
                }

                state == TrailingCommaState.REDUNDANT -> {
                    // remove redundant comma
                    lastElementOrComma.delete()
                    true
                }

                else -> false
            }
        }
    }

    private fun updatePsi(element: KtElement, updater: () -> Boolean) {
        val oldLength = element.parent.textLength
        if (!updater()) return

        val resultElement = element.reformatted(true)
        myPostProcessor.updateResultRange(oldLength, resultElement.parent.textLength)
    }

    private fun correctCommaPosition(parent: KtElement): Boolean {
        var hasChange = false
        for (pointerToComma in findInvalidCommas(parent).map { it.createSmartPointer() }) {
            pointerToComma.element?.let {
                correctComma(it)
                hasChange = true
            }
        }

        return hasChange || lineBreakIsMissing(parent)
    }

    fun process(formatted: PsiElement): PsiElement {
        LOG.assertTrue(formatted.isValid)
        formatted.accept(this)
        return formatted
    }

    fun processText(
        source: PsiFile,
        rangeToReformat: TextRange,
    ): TextRange {
        myPostProcessor.resultTextRange = rangeToReformat
        source.accept(this)
        return myPostProcessor.resultTextRange
    }

    companion object {
        private val LOG = Logger.getInstance(TrailingCommaVisitor::class.java)
    }
}

private fun PsiElement.addCommaAfter(factory: KtPsiFactory) {
    val comma = factory.createComma()
    parent.addAfter(comma, this)
}

private fun correctComma(comma: PsiElement) {
    val prevWithComment = comma.leafIgnoringWhitespace(false) ?: return
    val prevWithoutComment = comma.leafIgnoringWhitespaceAndComments(false) ?: return
    if (prevWithComment != prevWithoutComment) {
        val check = { element: PsiElement -> element is PsiWhiteSpace || element is PsiComment }
        val firstElement = prevWithComment.siblings(forward = false, withItself = true).takeWhile(check).last()
        val commentOwner = prevWithComment.parent
        comma.parent.addRangeAfter(firstElement, prevWithComment, comma)
        commentOwner.deleteChildRange(firstElement, prevWithComment)
    }
}
