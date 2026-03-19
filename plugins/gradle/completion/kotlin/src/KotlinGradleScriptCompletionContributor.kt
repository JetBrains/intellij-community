// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.kotlin

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class KotlinGradleScriptCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, insideScriptBlockPattern(DEPENDENCIES), KotlinGradleDependenciesCompletionProvider())
        extend(CompletionType.BASIC, insideBuildGradleKts(), KotlinGradleVersionCatalogCompletionProvider())
    }
}