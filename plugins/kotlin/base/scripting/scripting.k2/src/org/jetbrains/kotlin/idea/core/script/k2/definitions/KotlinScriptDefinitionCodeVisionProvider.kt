// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.definitions

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.ui.model.TextCodeVisionEntry
import com.intellij.codeInsight.hints.codeVision.DaemonBoundCodeVisionProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.core.script.k2.kotlinScriptDefinitionInlayHint
import org.jetbrains.kotlin.idea.core.script.k2.settings.KotlinScriptingSettingsConfigurable
import org.jetbrains.kotlin.idea.core.script.shared.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ide

class KotlinScriptDefinitionCodeVisionProvider : DaemonBoundCodeVisionProvider {
    override val id: String = "kotlin.script.definition.inlay.hint"
    override val groupId: String = KotlinScriptingGroupSettingProvider.GROUP_ID

    override val name: String = KotlinBaseScriptingBundle.message("hints.codevision.script.definition.name")
    override val relativeOrderings: List<CodeVisionRelativeOrdering> = listOf(CodeVisionRelativeOrdering.CodeVisionRelativeOrderingFirst)
    override val defaultAnchor: CodeVisionAnchorKind = CodeVisionAnchorKind.Top

    override fun computeForEditor(editor: Editor, file: PsiFile): List<Pair<TextRange, CodeVisionEntry>> {
        if (!Registry.`is`("enable.kotlin.code.vision.inlay", true)) return emptyList()
        if (file !is KtFile || !file.isScript()) return emptyList()

        val definition = file.findScriptDefinition() ?: return emptyList()

        val hintTextSupplier =
            definition.compilationConfiguration[ScriptCompilationConfiguration.ide.kotlinScriptDefinitionInlayHint] ?: return emptyList()
        val hintText = hintTextSupplier(definition.compilationConfiguration)

        val entry = TextCodeVisionEntry(hintText, id)

        return listOf(TextRange(0, 0) to entry)
    }

    override fun handleClick(editor: Editor, textRange: TextRange, entry: CodeVisionEntry) {
        val project = editor.project ?: return
        ShowSettingsUtil.getInstance().showSettingsDialog(project, KotlinScriptingSettingsConfigurable::class.java)
    }
}
