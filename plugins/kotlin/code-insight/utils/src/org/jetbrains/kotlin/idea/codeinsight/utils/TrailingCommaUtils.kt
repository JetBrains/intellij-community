// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.util.module

fun trailingCommaAllowedInModule(source: PsiElement): Boolean {
    return source.module
        ?.languageVersionSettings
        ?.supportsFeature(LanguageFeature.TrailingCommas) == true
}