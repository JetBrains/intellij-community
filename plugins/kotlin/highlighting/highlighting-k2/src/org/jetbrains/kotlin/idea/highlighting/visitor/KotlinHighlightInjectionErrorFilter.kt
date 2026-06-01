// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting.visitor

import com.intellij.codeInsight.highlighting.HighlightErrorFilter
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.kdoc.psi.api.KDocElement

internal class KotlinHighlightInjectionErrorFilter: HighlightErrorFilter() {
    override fun shouldHighlightErrorElement(element: PsiErrorElement): Boolean {
        if (element.language != KotlinLanguage.INSTANCE) return true

        val injectedLanguageManager = InjectedLanguageManager.getInstance(element.project)
        if (!injectedLanguageManager.isInjectedFragment(element.containingFile)) return true

        val injectionHost = injectedLanguageManager.getInjectionHost(element)

        val shouldBeIgnored =
            injectionHost is KDocElement ||
                    element.errorDescription == "Package directive and imports are forbidden in code fragments"

        return !shouldBeIgnored
    }
}
