// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.java

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.CompletionWeigher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiJavaFile
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * This weigher is responsible for heavily deprioritizing Kotlin suspend functions when completion
 * is invoked in **Java** code.
 * Suspend functions are hard to use correctly from Java code and are rarely something that
 * the user would want to complete.
 */
internal class DeprioritizeSuspendWeigher : CompletionWeigher() {
    private enum class Weight {
        SUSPEND, NON_SUSPEND
    }

    override fun weigh(
        element: LookupElement,
        location: CompletionLocation
    ): Comparable<*>? {
        val originalFile = location.baseCompletionParameters.originalFile
        // this weigher is only enabled in Java files
        if (originalFile !is PsiJavaFile) return null

        val ktFunction = element.psiElement?.unwrapped as? KtNamedFunction ?: return Weight.NON_SUSPEND

        return if (ktFunction.hasModifier(KtTokens.SUSPEND_KEYWORD)) {
            Weight.SUSPEND
        } else {
            Weight.NON_SUSPEND
        }
    }
}
