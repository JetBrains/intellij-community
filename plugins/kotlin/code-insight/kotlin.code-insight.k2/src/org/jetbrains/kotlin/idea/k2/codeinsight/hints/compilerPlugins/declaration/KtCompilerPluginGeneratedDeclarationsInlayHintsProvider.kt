// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hints.compilerPlugins.declaration

import com.intellij.codeInsight.hints.*
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.codeinsight.hints.compilerPlugins.fileCanBeAffectedByCompilerPlugins
import org.jetbrains.kotlin.psi.KtFile

internal class KtCompilerPluginGeneratedDeclarationsInlayHintsProvider :
    InlayHintsProvider<KtCompilerPluginGeneratedDeclarationsInlayHintsProviderSettings> {
    override val group: InlayGroup
        get() = InlayGroup.OTHER_GROUP

    override fun createSettings(): KtCompilerPluginGeneratedDeclarationsInlayHintsProviderSettings {
        return KtCompilerPluginGeneratedDeclarationsInlayHintsProviderSettings()
    }

    override val name: String
        get() = KotlinBundle.message("hints.description.compiler.plugins.declarations")

    override val key: SettingsKey<KtCompilerPluginGeneratedDeclarationsInlayHintsProviderSettings>
        get() = KtCompilerPluginGeneratedDeclarationsInlayHintsProviderSettings.KEY

    override val previewText: String?
        get() = null

    override fun getProperty(key: String): String {
        return KotlinBundle.message(key)
    }

    override fun createConfigurable(settings: KtCompilerPluginGeneratedDeclarationsInlayHintsProviderSettings): ImmediateConfigurable {
        return KtCompilerPluginGeneratedDeclarationsInlayHintsProviderSettingsConfigurable(settings)
    }

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: KtCompilerPluginGeneratedDeclarationsInlayHintsProviderSettings,
        sink: InlayHintsSink,
    ): InlayHintsCollector? {
        val ktFile = file as? KtFile ?: return null
        val project = file.project
        if (!ktFile.fileCanBeAffectedByCompilerPlugins(project)) return null
        return KtCompilerPluginGeneratedDeclarationsInlayHintsProviderCollector(editor, settings, project)
    }
}


