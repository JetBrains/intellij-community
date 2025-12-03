// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.versionCatalog.toml.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType

private class KotlinGradleTomlCompletionContributor() : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, insideLibrariesTable(), KotlinGradleTomlCompletionProvider())
    }
}
