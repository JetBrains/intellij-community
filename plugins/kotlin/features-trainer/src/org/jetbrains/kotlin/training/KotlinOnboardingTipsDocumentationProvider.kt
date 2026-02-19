// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.training

import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFile
import training.onboarding.AbstractOnboardingTipsDocumentationProvider

class KotlinOnboardingTipsDocumentationProvider : AbstractOnboardingTipsDocumentationProvider(KtTokens.EOL_COMMENT) {
    override fun isLanguageFile(file: PsiFile): Boolean = file is KtFile
}