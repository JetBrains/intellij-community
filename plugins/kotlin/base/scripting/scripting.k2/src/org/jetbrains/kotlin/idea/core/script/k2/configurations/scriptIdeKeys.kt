// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.configurations

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptRefinedConfigurationResolver
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptWorkspaceModelManager
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import java.io.File
import kotlin.script.experimental.api.IdeScriptCompilationConfigurationKeys
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.util.PropertiesCollection

val IdeScriptCompilationConfigurationKeys.configurationResolverDelegate: PropertiesCollection.Key<() -> ScriptRefinedConfigurationResolver> by PropertiesCollection.key()
val IdeScriptCompilationConfigurationKeys.scriptWorkspaceModelManagerDelegate: PropertiesCollection.Key<() -> ScriptWorkspaceModelManager> by PropertiesCollection.key()

fun ScriptDefinition.getConfigurationResolver(project: Project): ScriptRefinedConfigurationResolver =
    compilationConfiguration[ScriptCompilationConfiguration.ide.configurationResolverDelegate]?.invoke()
        ?: DefaultScriptConfigurationHandler.getInstance(project)

fun ScriptDefinition.getWorkspaceModelManager(project: Project): ScriptWorkspaceModelManager =
    compilationConfiguration[ScriptCompilationConfiguration.ide.scriptWorkspaceModelManagerDelegate]?.invoke()
        ?: DefaultScriptConfigurationHandler.getInstance(project)

fun VirtualFile.relativeLocation(project: Project): String {
    val scriptPath = project.guessProjectDir()?.path?.let {
            FileUtil.getRelativePath(
                it,
                this.path,
                File.separatorChar
            )
        } ?: this.path
    return scriptPath.replace(VfsUtilCore.VFS_SEPARATOR_CHAR, ':')
}
