// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.ui.IconManager
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.core.script.configurations.KotlinScriptService
import org.jetbrains.kotlin.idea.core.script.definition.editorToolbar
import org.jetbrains.kotlin.idea.core.script.statistics.KotlinScriptingSettingsCollector
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptConfigurationsProvider
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.isNonScript
import javax.swing.Icon
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ide

private val SHOW_NOTIFICATION = Key.create<Boolean>("SHOW_NOTIFICATION")

private val RELOAD_NEEDED_ICON: Icon = IconManager.getInstance().withIconBadge(KotlinIcons.LOAD_SCRIPT_CONFIGURATION, JBUI.CurrentTheme.IconBadge.INFORMATION)

internal class ReloadScriptConfigurationAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val ktFile = getProjectKtFile(e.dataContext) ?: return

        ReloadScriptConfigurationService.getInstance(project).reloadScriptData(ktFile)
    }

    override fun update(e: AnActionEvent) {
        val ktFile = getProjectKtFile(e.dataContext)
        val configuration = ktFile?.findScriptDefinition()?.compilationConfiguration
        if (ktFile == null || configuration?.get(ScriptCompilationConfiguration.ide.editorToolbar) == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        e.presentation.isEnabledAndVisible = true
        e.presentation.text = KotlinBaseScriptingBundle.message("reload.script.configuration.text", ktFile.name)
        val reloadNeeded = !ktFile.alwaysVirtualFile.scriptDiagnostics.isNullOrEmpty() || ktFile.getUserData(SHOW_NOTIFICATION) == true
        e.presentation.icon = if (reloadNeeded) RELOAD_NEEDED_ICON else KotlinIcons.LOAD_SCRIPT_CONFIGURATION
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

@Service(Service.Level.PROJECT)
class ReloadScriptConfigurationService(private val project: Project, private val scope: CoroutineScope) : Disposable {
    init {
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                scope.launch {
                    val virtualFile = FileDocumentManager.getInstance().getFile(event.document)?.takeIf { it.isInLocalFileSystem }
                    if (virtualFile == null || virtualFile.isNonScript() || !virtualFile.name.endsWith(".main.kts")) return@launch

                    val ktFile = readAction { PsiManager.getInstance(project).findFile(virtualFile) } as? KtFile
                    ktFile?.putUserData(SHOW_NOTIFICATION, true)
                }
            }
        }, this)
    }

    fun reloadScriptData(ktFile: KtFile) {
        val virtualFile = ktFile.alwaysVirtualFile

        scope.launch {
            scriptingDebugLog(virtualFile) { "reloadScriptData started" }
            KotlinScriptService.getInstance(project).reload(virtualFile)
            val configurationResult = project.service<ScriptConfigurationsProvider>().getScriptConfigurationResult(ktFile)

            readAction { ktFile.findScriptDefinition() }?.let {
                KotlinScriptingSettingsCollector.logConfigurationReloaded(project, it, configurationResult != null)
            }

            val notificationManager = NotificationGroupManager.getInstance()
            if (configurationResult != null) {
                scriptingInfoLog("reloadScriptData succeeded: ${virtualFile.path}")
                notificationManager.getNotificationGroup("KotlinScriptNotificationGroup")
                    .createNotification(
                        KotlinBaseScriptingBundle.message("script.configuration.loading.title"),
                        KotlinBaseScriptingBundle.message("script.configuration.loading.success", virtualFile.name),
                        NotificationType.INFORMATION
                    ).notify(project)
            } else {
                scriptingWarnLog("reloadScriptData failed: ${virtualFile.path}")
            }

            ktFile.putUserData(SHOW_NOTIFICATION, false)
        }
    }

    override fun dispose() {}

    companion object {
        @JvmStatic
        fun getInstance(project: Project): ReloadScriptConfigurationService = project.service()
    }
}

private fun getProjectKtFile(context: DataContext): KtFile? {
    val project = context.getData(PROJECT) ?: return null
    val editor = context.getData(CommonDataKeys.EDITOR)
    return if (editor != null) {
        PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
    } else {
        context.getData(CommonDataKeys.PSI_FILE)
    } as? KtFile
}
