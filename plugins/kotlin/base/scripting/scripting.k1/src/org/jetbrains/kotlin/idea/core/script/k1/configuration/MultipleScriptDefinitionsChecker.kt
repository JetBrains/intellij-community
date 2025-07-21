// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.k1.configuration

import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.createComponentActionLabel
import org.jetbrains.kotlin.idea.core.script.k1.settings.KotlinScriptingSettingsImpl
import org.jetbrains.kotlin.idea.core.script.v1.IdeScriptDefinitionProvider
import org.jetbrains.kotlin.idea.util.isKotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.KotlinScriptDefinitionFromAnnotatedTemplate
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import java.util.function.Function
import javax.swing.JComponent

class MultipleScriptDefinitionsChecker : EditorNotificationProvider {

    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
        if (!file.isKotlinFileType()) return null

        val ktFile = (PsiManager.getInstance(project).findFile(file) as? KtFile)?.takeIf(KtFile::isScript) ?: return null

        if (KotlinScriptingSettingsImpl.getInstance(project).suppressDefinitionsCheck) return null

        val applicableDefinitions = IdeScriptDefinitionProvider.getInstance(project).getDefinitions().filter {
                !it.isDefault && it.isScript(KtFileScriptSource(ktFile)) && KotlinScriptingSettingsImpl.getInstance(project)
                    .isScriptDefinitionEnabled(it)
            }.toList()
        if (applicableDefinitions.size < 2 || applicableDefinitions.all { it.isGradleDefinition() }) return null

        return Function { fileEditor: FileEditor ->
            createNotification(fileEditor, project, applicableDefinitions)
        }
    }

    private fun ScriptDefinition.isGradleDefinition(): Boolean {
        val pattern = (this as? ScriptDefinition.FromConfigurationsBase)?.fileNamePattern ?: return false
        return pattern.endsWith("\\.gradle\\.kts") || pattern.endsWith("\\.gradle\\.kts$") || fileExtension.endsWith("gradle.kts")
    }

    private fun createNotification(fileEditor: FileEditor, project: Project, defs: List<ScriptDefinition>): EditorNotificationPanel =
        EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info).apply {
            text = KotlinBundle.message("script.text.multiple.script.definitions.are.applicable.for.this.script", defs.first().name)
            createComponentActionLabel(
                KotlinBundle.message("script.action.text.show.all")
            ) { label ->
                val list = JBPopupFactory.getInstance().createListPopup(
                    object : BaseListPopupStep<ScriptDefinition>(null, defs) {
                        override fun getTextFor(value: ScriptDefinition): String {
                            @NlsSafe
                            val text = value.asLegacyOrNull<KotlinScriptDefinitionFromAnnotatedTemplate>()?.let {
                                it.name + " (${it.scriptFilePattern})"
                            } ?: (value.name + " (${value.fileExtension})")
                            return text
                        }
                    }
                )
                list.showUnderneathOf(label)
            }

            createActionLabel(KotlinBundle.message("script.action.text.ignore")) {
                KotlinScriptingSettingsImpl.getInstance(project).suppressDefinitionsCheck = true
                EditorNotifications.getInstance(project).updateAllNotifications()
            }

            createActionLabel(KotlinBundle.message("script.action.text.open.settings")) {
                ShowSettingsUtilImpl.showSettingsDialog(project, "preferences.language.Kotlin.scripting", "")
            }
        }
}
