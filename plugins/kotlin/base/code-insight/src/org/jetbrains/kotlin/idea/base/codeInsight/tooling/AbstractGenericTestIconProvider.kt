// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight.tooling

import org.jetbrains.kotlin.idea.base.codeInsight.KotlinTestAvailabilityChecker
import org.jetbrains.kotlin.idea.base.codeInsight.isFrameworkAvailable
import org.jetbrains.kotlin.idea.highlighter.KotlinTestRunLineMarkerContributor
import org.jetbrains.kotlin.idea.testIntegration.genericKotlinTestUrls
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import javax.swing.Icon

abstract class AbstractGenericTestIconProvider {
    abstract fun isKotlinTestDeclaration(declaration: KtNamedDeclaration): Boolean

    fun getTestContainerElement(declaration: KtNamedDeclaration): KtClassOrObject? {
        return when (declaration) {
            is KtClassOrObject -> declaration
            is KtNamedFunction -> declaration.containingClassOrObject
            else -> null
        }
    }

    fun getGenericTestIcon(declaration: KtNamedDeclaration, initialLocations: List<String>): Icon? {
        if (!isFrameworkAvailable<KotlinTestAvailabilityChecker>(declaration)) {
            return null
        }

        if (declaration.hasModifier(KtTokens.PRIVATE_KEYWORD)) return null

        if (!isKotlinTestDeclaration(declaration)) {
            return null
        }

        val testContainer = getTestContainerElement(declaration) ?: return null
        if (!isKotlinTestDeclaration(testContainer)) {
            return null
        }

        return KotlinTestRunLineMarkerContributor.getTestStateIcon(declaration.genericKotlinTestUrls(), declaration)
    }

    private fun Collection<String>.joinWithEscape(delimiterChar: Char): String {
        if (isEmpty()) return ""

        val expectedSize = sumOf { it.length } + size - 1
        val out = StringBuilder(expectedSize)
        var first = true
        for (s in this) {
            if (!first) {
                out.append(delimiterChar)
            }
            first = false
            for (ch in s) {
                if (ch == delimiterChar || ch == '\\') {
                    out.append('\\')
                }
                out.append(ch)
            }
        }
        return out.toString()
    }
}