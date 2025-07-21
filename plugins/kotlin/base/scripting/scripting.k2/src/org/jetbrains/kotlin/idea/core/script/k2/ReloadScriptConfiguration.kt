// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.toolbar.floating.AbstractFloatingToolbarProvider
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarComponent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.util.application
import com.intellij.util.messages.Topic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.core.script.shared.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.core.script.k2.ReloadScriptConfigurationService.Companion.TOPIC
import org.jetbrains.kotlin.idea.core.script.k2.configurations.getConfigurationResolver
import org.jetbrains.kotlin.idea.core.script.k2.definitions.ScriptDefinitionProviderImpl
import org.jetbrains.kotlin.idea.core.script.k2.highlighting.DefaultScriptResolutionStrategy
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptModuleManager.Companion.removeScriptModules
import org.jetbrains.kotlin.idea.core.script.shared.scriptDiagnostics
import org.jetbrains.kotlin.idea.core.script.shared.alwaysVirtualFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.isNonScript

@Language("devkit-action-id")
private const val ACTION_GROUP = "KotlinScripts.ReloadConfigurationActionGroup"

private val SHOW_NOTIFICATION = Key.create<Boolean>("SHOW_NOTIFICATION")

@ApiStatus.Internal
class ScriptConfigurationFloatingToolbarProvider : AbstractFloatingToolbarProvider(ACTION_GROUP) {
    override val autoHideable: Boolean = false

    override fun isApplicable(dataContext: DataContext): Boolean {
        val ktFile = getProjectKtFile(dataContext) ?: return false
        return ktFile.name.endsWith(".kts")
    }

    override fun register(dataContext: DataContext, component: FloatingToolbarComponent, parentDisposable: Disposable) {
        val project = dataContext.getData(PROJECT) ?: return
        val service = ReloadScriptConfigurationService.getInstance(project)

        application.messageBus.connect(parentDisposable)
            .subscribe(TOPIC, object : ReloadScriptConfigurationService.Listener {
                override fun onNotificationChanged(virtualFile: VirtualFile) {
                    service.handleFloatingToolbarComponent(virtualFile, component)
                }
            })
    }
}

internal class ReloadScriptConfiguration : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val ktFile = getProjectKtFile(e.dataContext) ?: return

        ReloadScriptConfigurationService.getInstance(project).reloadScriptData(ktFile)
    }

    override fun update(e: AnActionEvent) {
        val ktFile = getProjectKtFile(e.dataContext)
        if (ktFile != null && ktFile.name.endsWith(".kts")) {
            e.presentation.isEnabledAndVisible = true
            e.presentation.text = KotlinBaseScriptingBundle.message("reload.script.configuration.text", ktFile.name)
        } else {
            e.presentation.isEnabledAndVisible = false
        }
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
                    if (ktFile != null) {
                        ktFile.putUserData(SHOW_NOTIFICATION, true)
                        ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).onNotificationChanged(virtualFile)
                    }
                }
            }
        }, this)
    }

    fun reloadScriptData(ktFile: KtFile) {
        val definition = ktFile.findScriptDefinition() ?: return
        val virtualFile = ktFile.alwaysVirtualFile

        scope.launch {
            definition.getConfigurationResolver(project).remove(virtualFile)
            project.removeScriptModules(listOf(virtualFile))
            ScriptDefinitionProviderImpl.getInstance(project).notifyDefinitionsChanged()
            DefaultScriptResolutionStrategy.getInstance(project).execute(ktFile).join()

            ktFile.putUserData(SHOW_NOTIFICATION, false)
            ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).onNotificationChanged(virtualFile)
        }
    }

    fun handleFloatingToolbarComponent(virtualFile: VirtualFile, component: FloatingToolbarComponent) {
        scope.launch {
            val ktFile = readAction { PsiManager.getInstance(project).findFile(virtualFile) } as? KtFile
            val shouldShow = !virtualFile.scriptDiagnostics.isNullOrEmpty() || ktFile?.getUserData(SHOW_NOTIFICATION) ?: false

            withContext(Dispatchers.EDT) {
                if (shouldShow) {
                    component.scheduleShow()
                } else {
                    component.scheduleHide()
                }
            }
        }
    }

    override fun dispose() {}

    interface Listener {
        fun onNotificationChanged(virtualFile: VirtualFile) {}
    }

    companion object {
        @JvmField
        @Topic.ProjectLevel
        val TOPIC: Topic<Listener> = Topic.create("ReloadScriptConfigurationService", Listener::class.java)

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
