// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleId
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.core.script.KOTLIN_SCRIPTS_MODULE_NAME
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import java.io.File
import javax.swing.Icon
import kotlin.script.experimental.api.IdeScriptCompilationConfigurationKeys
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.util.PropertiesCollection


class NewScriptFileInfo(
    var id: String = "", var title: String = "", var templateName: String = "Kotlin Script", var icon: Icon = KotlinIcons.SCRIPT
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NewScriptFileInfo

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

val IdeScriptCompilationConfigurationKeys.kotlinScriptTemplateInfo: PropertiesCollection.Key<NewScriptFileInfo> by PropertiesCollection.key()

val IdeScriptCompilationConfigurationKeys.configurationResolverDelegate: PropertiesCollection.Key<() -> ScriptRefinedConfigurationResolver> by PropertiesCollection.key()
val IdeScriptCompilationConfigurationKeys.scriptWorkspaceModelManagerDelegate: PropertiesCollection.Key<() -> ScriptWorkspaceModelManager> by PropertiesCollection.key()

fun ScriptDefinition.getConfigurationResolver(project: Project): ScriptRefinedConfigurationResolver =
    compilationConfiguration[ScriptCompilationConfiguration.ide.configurationResolverDelegate]?.invoke()
        ?: DefaultScriptConfigurationHandler.getInstance(project)

fun ScriptDefinition.getWorkspaceModelManager(project: Project): ScriptWorkspaceModelManager =
    compilationConfiguration[ScriptCompilationConfiguration.ide.scriptWorkspaceModelManagerDelegate]?.invoke()
        ?: DefaultScriptConfigurationHandler.getInstance(project)

interface ScriptRefinedConfigurationResolver {
    suspend fun create(virtualFile: VirtualFile, definition: ScriptDefinition): ScriptConfigurationWithSdk?
    fun get(virtualFile: VirtualFile): ScriptConfigurationWithSdk?
}

interface ScriptWorkspaceModelManager {
    suspend fun updateWorkspaceModel(configurationPerFile: Map<VirtualFile, ScriptConfigurationWithSdk>)

    fun isModuleExist(
        project: Project, scriptFile: VirtualFile, definition: ScriptDefinition
    ): Boolean = project.workspaceModel.currentSnapshot.contains(getModuleId(project, scriptFile, definition))

    fun getModuleId(
        project: Project, scriptFile: VirtualFile, definition: ScriptDefinition
    ): ModuleId {
        val scriptModuleLocation = project.scriptModuleRelativeLocation(scriptFile)
        return ModuleId("$KOTLIN_SCRIPTS_MODULE_NAME.${definition.name}.$scriptModuleLocation")
    }
}

fun Project.scriptModuleRelativeLocation(scriptFile: VirtualFile): String {
    val scriptPath = this.guessProjectDir()?.path?.let {
            FileUtil.getRelativePath(
                it,
                scriptFile.path,
                File.separatorChar
            )
        } ?: scriptFile.path
    return scriptPath.replace(VfsUtilCore.VFS_SEPARATOR_CHAR, ':')
}
