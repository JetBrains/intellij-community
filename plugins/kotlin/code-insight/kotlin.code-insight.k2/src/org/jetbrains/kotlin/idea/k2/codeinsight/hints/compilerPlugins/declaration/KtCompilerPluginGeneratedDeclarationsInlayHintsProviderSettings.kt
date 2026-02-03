// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hints.compilerPlugins.declaration

import com.intellij.codeInsight.hints.SettingsKey

internal data class KtCompilerPluginGeneratedDeclarationsInlayHintsProviderSettings(
    @JvmField var showHiddenMembers: Boolean = false,
) {
    companion object {
        val KEY: SettingsKey<KtCompilerPluginGeneratedDeclarationsInlayHintsProviderSettings> =
            SettingsKey<KtCompilerPluginGeneratedDeclarationsInlayHintsProviderSettings>("kotlin.compiler.plugins.declarations")
    }
}