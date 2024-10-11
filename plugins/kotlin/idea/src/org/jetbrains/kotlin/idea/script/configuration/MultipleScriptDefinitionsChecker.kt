// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.script.configuration

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
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager
import org.jetbrains.kotlin.idea.core.script.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.idea.util.isKotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.KotlinScriptDefinitionFromAnnotatedTemplate
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.util.function.Function
import javax.swing.JComponent

class MultipleScriptDefinitionsChecker : EditorNotificationProvider {

    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
        if (!file.isKotlinFileType()) return null

        val ktFile = PsiManager.getInstance(project).findFile(file).safeAs<KtFile>()?.takeIf(KtFile::isScript) ?: return null

        if (KotlinScriptingSettings.getInstance(project).suppressDefinitionsCheck) return null

      val allApplicableDefinitions = ScriptDefinitionsManager.getInstance(project)
        .allDefinitions
        .filter {
          it.isScript(KtFileScriptSource(ktFile)) &&
          KotlinScriptingSettings.getInstance(project).isScriptDefinitionEnabled(it)
        }
        .toList()
        if (allApplicableDefinitions.size < 2 || areDefinitionsForGradleKts(allApplicableDefinitions)) return null

        return Function { fileEditor: FileEditor ->
            createNotification(fileEditor, project, allApplicableDefinitions)
        }
    }

    private fun areDefinitionsForGradleKts(allApplicableDefinitions: List<ScriptDefinition>): Boolean {
        return allApplicableDefinitions.all { definition ->
            definition.asLegacyOrNull<KotlinScriptDefinitionFromAnnotatedTemplate>()?.let {
                val pattern = it.scriptFilePattern.pattern
                return@all pattern.endsWith("\\.gradle\\.kts") || pattern.endsWith("\\.gradle\\.kts$")
            }
            definition.fileExtension.endsWith("gradle.kts")
        }
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
                KotlinScriptingSettings.getInstance(project).suppressDefinitionsCheck = true
                EditorNotifications.getInstance(project).updateAllNotifications()
            }

            createActionLabel(KotlinBundle.message("script.action.text.open.settings")) {
                ShowSettingsUtilImpl.showSettingsDialog(project, KotlinScriptingSettingsConfigurable.ID, "")
            }
        }
}
