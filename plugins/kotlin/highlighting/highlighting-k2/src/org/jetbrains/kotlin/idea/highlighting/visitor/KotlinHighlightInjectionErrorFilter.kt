// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting.visitor

import com.intellij.codeInsight.highlighting.HighlightErrorFilter
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.kdoc.psi.api.KDocElement

internal class KotlinHighlightInjectionErrorFilter: HighlightErrorFilter() {
    override fun shouldHighlightErrorElement(element: PsiErrorElement): Boolean {
        val injectedLanguageManager = InjectedLanguageManager.getInstance(element.project)
        val injectionHost = injectedLanguageManager.getInjectionHost(element)
        if (injectionHost is KDocElement) {
            return false
        }

        val shouldBeIgnored = element.language == KotlinLanguage.INSTANCE &&
            element.errorDescription == "Package directive and imports are forbidden in code fragments" &&
            injectedLanguageManager.isInjectedFragment(element.containingFile)

        return !shouldBeIgnored
    }
}
