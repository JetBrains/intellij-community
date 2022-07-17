// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.codevision

import com.intellij.codeInsight.hints.*
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiFile
import com.intellij.ui.layout.*
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.KotlinLanguage
import javax.swing.JPanel

class KotlinCodeVisionProvider : InlayHintsProvider<NoSettings> {

    override val key: SettingsKey<NoSettings> = SettingsKey("CodeVision")
    override val name: String = KotlinBundle.message("hints.title.codevision")
    override val group: InlayGroup
        get() = InlayGroup.CODE_VISION_GROUP
    override val previewText: String? = null

    var usagesLimit: Int = 100
    var inheritorsLimit: Int = 100

    var showUsages: Boolean = false
    var showInheritors: Boolean = false

    override val isVisibleInSettings: Boolean = false

    override fun isLanguageSupported(language: Language): Boolean = language is KotlinLanguage

    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable = object : ImmediateConfigurable {
        override fun createComponent(listener: ChangeListener): JPanel = panel {}

        override val cases: List<ImmediateConfigurable.Case> = emptyList()

        override val mainCheckboxText: String = ""
    }

    override fun createSettings(): NoSettings = NoSettings()

    override fun getCollectorFor(
        file: PsiFile, editor: Editor, settings: NoSettings, sink: InlayHintsSink
    ): InlayHintsCollector? {

        // It's highly discouraged to use global settings from the test environment. Tests launched in parallel might be affected, e.g.
        // MultiFileHighlightingTestGenerated. Hence the properties 'showUsages' and 'showInheritors'.

        val showUsagesResolved = Registry.`is`("kotlin.code-vision.usages", false) || showUsages
        val showInheritorsResolved = Registry.`is`("kotlin.code-vision.inheritors", false) || showInheritors

        if (!showUsagesResolved && !showInheritorsResolved) return null

        return KotlinCodeVisionHintsCollector(editor, showUsagesResolved, showInheritorsResolved, usagesLimit, inheritorsLimit)
    }
}