// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.definitions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.InspectionWidgetActionProvider
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.core.script.shared.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.core.script.k2.settings.KotlinScriptingSettingsConfigurable
import org.jetbrains.kotlin.idea.core.script.v1.kotlinScriptTemplateInfo
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ide

class KotlinScriptDefinitionInfoWidgetProvider : InspectionWidgetActionProvider {
    override fun createAction(editor: Editor): AnAction? {
        val project = editor.project ?: return null
        val initialPsiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return null
        if (!initialPsiFile.name.endsWith(".kts")) return null

        return object : AnAction() {
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

            override fun update(e: AnActionEvent) {
                val editorInContext = e.getData(CommonDataKeys.EDITOR)
                val psiFile = editorInContext?.document?.let {
                    PsiDocumentManager.getInstance(project).getPsiFile(it)
                }
                val definition = psiFile?.findScriptDefinition()
                if (definition == null) {
                    e.presentation.isEnabledAndVisible = false
                    return
                }
                val info = definition.compilationConfiguration[ScriptCompilationConfiguration.ide.kotlinScriptTemplateInfo]
                e.presentation.isEnabledAndVisible = true
                e.presentation.text = info?.title ?: definition.name
                e.presentation.icon = info?.icon ?: KotlinIcons.SCRIPT
                e.presentation.description = info?.description?.takeIf { it.isNotEmpty() }
                    ?: KotlinBaseScriptingBundle.message("tooltip.this.definition.used.for.current.kotlin.script.configuration")
            }

            override fun actionPerformed(e: AnActionEvent) {
                val p = e.project ?: return
                ShowSettingsUtil.getInstance().showSettingsDialog(p, KotlinScriptingSettingsConfigurable::class.java)
            }
        }
    }
}
