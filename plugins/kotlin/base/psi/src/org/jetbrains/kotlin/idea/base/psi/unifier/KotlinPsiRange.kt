// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.psi.unifier

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.siblings
import java.util.*

sealed interface KotlinPsiRange {
    object Empty : KotlinPsiRange {
        override val elements: List<PsiElement>
            get() = Collections.emptyList()

        override val textRange: TextRange
            get() = TextRange.EMPTY_RANGE
    }

    class ListRange(override val elements: List<PsiElement>) : KotlinPsiRange {
        override val textRange: TextRange
            get() {
                val startRange = elements.first().textRange
                val endRange = elements.last().textRange
                if (startRange == null || endRange == null) return TextRange.EMPTY_RANGE

                return TextRange(startRange.startOffset, endRange.endOffset)
            }
    }

    class SmartListRange(elements: List<PsiElement>) : KotlinPsiRange {

        private val pointers: List<SmartPsiElementPointer<PsiElement>> = elements.map { SmartPointerManager.createPointer(it) }

        override val elements: List<PsiElement>
            get() {
                val elements: List<PsiElement> = pointers.mapNotNull { pointer -> pointer.element }
                return elements.takeIf { elements.size == pointers.size } ?: emptyList()
            }

        override val textRange: TextRange
            get() {
                val elements = this.elements
                return if (elements.isNotEmpty()) {
                    TextRange(elements.first().textRange.startOffset, elements.last().textRange.endOffset)
                } else {
                    TextRange.EMPTY_RANGE
                }
            }
    }

    val elements: List<PsiElement>
    val textRange: TextRange

    val isValid: Boolean
        get() = elements.all { it.isValid }

    val isEmpty: Boolean
        get() = elements.isEmpty()

    operator fun contains(element: PsiElement): Boolean {
        return textRange.contains(element.textRange ?: TextRange.EMPTY_RANGE)
    }

    fun <T> match(
        scope: PsiElement,
        unifier: (KotlinPsiRange, KotlinPsiRange) -> KotlinPsiUnificationResult.Success<T>?
    ): List<KotlinPsiUnificationResult.Success<T>> {
        val elements = elements.filter(::isSignificant)
        if (elements.isEmpty()) return Collections.emptyList()

        val matches = ArrayList<KotlinPsiUnificationResult.Success<T>>()
        scope.accept(
            object : KtTreeVisitorVoid() {
                override fun visitKtElement(element: KtElement) {
                    val range = element
                        .siblings()
                        .filter(::isSignificant)
                        .take(elements.size)
                        .toList()
                        .toRange()

                    val result = unifier(range, this@KotlinPsiRange)

                    if (result is KotlinPsiUnificationResult.StrictSuccess) {
                        matches.add(result)
                    } else {
                        val matchCountSoFar = matches.size
                        super.visitKtElement(element)
                        if (result is KotlinPsiUnificationResult.WeakSuccess && matches.size == matchCountSoFar) {
                            matches.add(result)
                        }
                    }
                }
            }
        )
        return matches
    }
}

fun List<PsiElement>.toRange(significantOnly: Boolean = true): KotlinPsiRange {
    val elements = if (significantOnly) filter(::isSignificant) else this
    return if (elements.isEmpty()) KotlinPsiRange.Empty else KotlinPsiRange.ListRange(elements)
}

fun PsiElement?.toRange(): KotlinPsiRange {
    if (this == null) return KotlinPsiRange.Empty
    return KotlinPsiRange.ListRange(Collections.singletonList(this))
}

private fun isSignificant(element: PsiElement): Boolean {
    return element !is PsiWhiteSpace && element !is PsiComment && element.textLength > 0
}