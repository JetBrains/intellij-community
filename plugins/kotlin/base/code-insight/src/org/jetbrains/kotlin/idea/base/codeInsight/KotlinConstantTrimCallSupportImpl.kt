// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.editor.KotlinConstantTrimCallSupport

internal class KotlinConstantTrimCallSupportImpl : KotlinConstantTrimCallSupport {
    override fun isTrimCallAllowedInConstantContext(element: PsiElement): Boolean =
        element.languageVersionSettings.supportsFeature(LanguageFeature.IntrinsicConstEvaluation)
}
