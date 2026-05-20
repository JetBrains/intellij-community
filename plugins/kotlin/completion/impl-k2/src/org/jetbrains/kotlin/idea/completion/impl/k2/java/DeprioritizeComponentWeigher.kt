// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.java

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.CompletionWeigher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiJavaFile
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

/**
 * This weigher is responsible for deprioritizing Kotlin data class component functions when completion
 * is invoked in **Java** code.
 * While component functions can be used correctly from Java, they are only meant for Kotlin
 * destructuring. For other cases, it is better to use the named getters for parameters.
 */
internal class DeprioritizeComponentWeigher : CompletionWeigher() {
    private enum class Weight {
        COMPONENT, NON_COMPONENT
    }

    override fun weigh(
        element: LookupElement,
        location: CompletionLocation
    ): Comparable<*>? {
        val originalFile = location.baseCompletionParameters.originalFile
        // this weigher is only enabled in Java files
        if (originalFile !is PsiJavaFile) return null

        val ktDeclaration = element.psiElement?.unwrapped as? KtDeclaration
        if (ktDeclaration !is KtParameter && ktDeclaration !is KtNamedFunction) return Weight.NON_COMPONENT

        val containingClass = ktDeclaration.containingClassOrObject
        if (containingClass?.isData() != true) return Weight.NON_COMPONENT

        return if (element.lookupString.matches(COMPONENT_REGEX)) {
            Weight.COMPONENT
        } else {
            Weight.NON_COMPONENT
        }
    }
}

private val COMPONENT_REGEX = Regex("component\\d+")
