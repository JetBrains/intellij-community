// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.scripting

import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFileBase
import org.gradle.tooling.model.kotlin.dsl.KotlinDslModelsParameters
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.core.script.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.idea.gradle.KotlinIdeaGradleBundle
import org.jetbrains.kotlin.idea.gradleJava.scripting.importing.KotlinDslScriptModelResolver
import org.jetbrains.kotlin.idea.gradleJava.scripting.roots.GradleBuildRoot
import org.jetbrains.kotlin.idea.gradleJava.scripting.roots.GradleBuildRootsManager
import org.jetbrains.kotlin.idea.util.isKotlinFileType
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.service.project.GradlePartialResolverPolicy
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.util.GradleConstants

val scriptConfigurationsNeedToBeUpdatedBalloon
    get() = Registry.`is`("kotlin.gradle.scripts.scriptConfigurationsNeedToBeUpdatedFloatingNotification", true)

fun runPartialGradleImportForAllRoots(project: Project) {
    GradleBuildRootsManager.getInstance(project)?.getAllRoots()?.forEach { root ->
        runPartialGradleImport(project, root)
    }
}

fun runPartialGradleImport(project: Project, root: GradleBuildRoot) {
    if (root.isImportingInProgress()) return

    ExternalSystemUtil.refreshProject(
        root.pathPrefix,
        ImportSpecBuilder(project, GradleConstants.SYSTEM_ID)
            .withVmOptions(
                "-D${KotlinDslModelsParameters.PROVIDER_MODE_SYSTEM_PROPERTY_NAME}=" +
                        KotlinDslModelsParameters.CLASSPATH_MODE_SYSTEM_PROPERTY_VALUE
            )
            .projectResolverPolicy(
                GradlePartialResolverPolicy { it is KotlinDslScriptModelResolver }
            )
    )
}

@Nls fun configurationsAreMissingRequestNeeded() = KotlinIdeaGradleBundle.message("notification.wasNotImportedAfterCreation.text")
@Nls fun getConfigurationsActionText() = KotlinIdeaGradleBundle.message("action.text.load.script.configurations")
@Nls fun configurationsAreMissingRequestNeededHelp(): String = KotlinIdeaGradleBundle.message("notification.wasNotImportedAfterCreation.help")
@Nls fun configurationsAreMissingAfterRequest(): String = KotlinIdeaGradleBundle.message("notification.notEvaluatedInLastImport.text")

fun autoReloadScriptConfigurations(project: Project, file: VirtualFile): Boolean {
    val definition = file.findScriptDefinition(project) ?: return false

    return KotlinScriptingSettings.getInstance(project).autoReloadConfigurations(definition)
}

fun scriptConfigurationsNeedToBeUpdated(project: Project, file: VirtualFile) {
    if (autoReloadScriptConfigurations(project, file)) {
        GradleBuildRootsManager.getInstance(project)?.getScriptInfo(file)?.buildRoot?.let {
            runPartialGradleImport(project, it)
        }
    } else {
        // notification is shown in LoadConfigurationAction
    }
}

fun scriptConfigurationsAreUpToDate(project: Project): Boolean = true

class LoadConfigurationAction : AnAction(
    KotlinIdeaGradleBundle.message("action.text.load.script.configurations"),
    KotlinIdeaGradleBundle.message("action.description.load.script.configurations"),
    KotlinIcons.LOAD_SCRIPT_CONFIGURATION
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = getKotlinScriptFile(editor) ?: return
        val root = GradleBuildRootsManager.getInstance(project)?.getScriptInfo(file)?.buildRoot ?: return

        runPartialGradleImport(project, root)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        ensureValidActionVisibility(e)
    }

    private fun ensureValidActionVisibility(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return

        e.presentation.isVisible = getNotificationVisibility(editor)
    }

    private fun getNotificationVisibility(editor: Editor): Boolean {
        if (!scriptConfigurationsNeedToBeUpdatedBalloon) return false
        if (DiffUtil.isDiffEditor(editor)) return false

        val project = editor.project ?: return false

        // prevent services initialization
        // (all services actually initialized under the ScriptDefinitionProvider during startup activity)
        if (ScriptDefinitionProvider.getServiceIfCreated(project) == null) return false

        val file = getKotlinScriptFile(editor) ?: return false

        if (autoReloadScriptConfigurations(project, file)) {
            return false
        }

        return GradleBuildRootsManager.getInstance(project)?.isConfigurationOutOfDate(file) ?: false
    }

    private fun getKotlinScriptFile(editor: Editor): VirtualFile? {
        return FileDocumentManager.getInstance()
            .getFile(editor.document)
            ?.takeIf {
                it !is LightVirtualFileBase
                        && it.isValid
                        && it.isKotlinFileType()
                        && isGradleKotlinScript(it)
            }
    }
}

fun getGradleVersion(project: Project, settings: GradleProjectSettings): String {
    return GradleInstallationManager.getGradleVersion(
        service<GradleInstallationManager>().getGradleHome(project, settings.externalProjectPath)?.path
    ) ?: GradleVersion.current().version
}
