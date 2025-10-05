// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hints.compilerPlugins.declaration

import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle

internal class KtCompilerPluginGeneratedDeclarationsInlayHintsProviderSettingsConfigurable(
    private val settings: KtCompilerPluginGeneratedDeclarationsInlayHintsProviderSettings
) : ImmediateConfigurable {
    override fun createComponent(listener: ChangeListener) = panel {
        row {
            checkBox(KotlinBundle.message("hints.settings.compiler.plugins.declarations.show.hidden.name"))
                .applyToComponent { isSelected = settings.showHiddenMembers }
                .onChanged {
                    settings.showHiddenMembers = it.isSelected
                    listener.settingsChanged()
                }
                .comment(KotlinBundle.message("hints.settings.compiler.plugins.declarations.show.hidden.description"))
        }
    }

    override val mainCheckboxText: String = KotlinBundle.message("hints.settings.common.items")
}