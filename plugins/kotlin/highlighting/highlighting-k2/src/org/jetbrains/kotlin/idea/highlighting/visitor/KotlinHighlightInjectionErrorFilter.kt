// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting.visitor

import com.intellij.codeInsight.highlighting.HighlightErrorFilter
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.idea.KotlinLanguage

internal class KotlinHighlightInjectionErrorFilter: HighlightErrorFilter() {
    override fun shouldHighlightErrorElement(element: PsiErrorElement): Boolean {
        if (element.language == KotlinLanguage.INSTANCE &&
            element.errorDescription == "Package directive and imports are forbidden in code fragments" &&
            InjectedLanguageManager.getInstance(element.project).isInjectedFragment(element.containingFile)
        ) {
            return false
        }
        return true
    }
}
