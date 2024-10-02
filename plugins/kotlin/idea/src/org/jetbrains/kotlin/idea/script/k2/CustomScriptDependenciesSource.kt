// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.script.k2

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.base.util.runReadActionInSmartMode
import org.jetbrains.kotlin.idea.core.script.KotlinScriptEntitySource
import org.jetbrains.kotlin.idea.core.script.SCRIPT_DEPENDENCIES_SOURCES
import org.jetbrains.kotlin.idea.core.script.getUpdatedStorage
import org.jetbrains.kotlin.idea.core.script.k2.BaseScriptModel
import org.jetbrains.kotlin.idea.core.script.k2.ScriptDependenciesData
import org.jetbrains.kotlin.idea.core.script.k2.ScriptDependenciesSource
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.io.File
import java.nio.file.Path
import kotlin.script.experimental.api.with
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm

class CustomScriptDependenciesSource(override val project: Project) : ScriptDependenciesSource<BaseScriptModel>(project) {
    override fun resolveDependencies(scripts: Iterable<BaseScriptModel>): ScriptDependenciesData {
        val sdk = ProjectRootManager.getInstance(project).projectSdk

        val configurations = scripts.associate {
            val scriptSource = VirtualFileScriptSource(it.virtualFile)
            val definition = findScriptDefinition(project, scriptSource)

            val providedConfiguration = sdk?.homePath
                ?.let {
                    definition.compilationConfiguration.with {
                        jvm.jdkHome(File(it))
                    }
                }

            it.virtualFile to project.runReadActionInSmartMode {
                refineScriptCompilationConfiguration(scriptSource, definition, project, providedConfiguration)
            }
        }

        return ScriptDependenciesData(
            configurations,
            sdks = sdk?.homePath?.let<@NonNls String, Map<Path, Sdk>> { mapOf(Path.of(it) to sdk) } ?: emptyMap()
        )
    }

    override suspend fun updateModules(dependencies: ScriptDependenciesData, storage: MutableEntityStorage?) {
        val updatedStorage = getUpdatedStorage(
            project, dependencies
        ) { KotlinCustomScriptModuleEntitySource(it) }

        val scriptFiles =
            dependencies.configurations.keys.toSet()

        project.workspaceModel.update("Updating MainKts Kotlin Scripts modules") {
            it.replaceBySource(
                { source ->
                    (source as? KotlinCustomScriptModuleEntitySource)?.let {
                        scriptFiles.contains(it.virtualFileUrl?.virtualFile)
                    } == true
                },
                updatedStorage
            )
        }
    }

    companion object {
        fun getInstance(project: Project): CustomScriptDependenciesSource? =
            SCRIPT_DEPENDENCIES_SOURCES.getExtensions(project)
                .filterIsInstance<CustomScriptDependenciesSource>().firstOrNull()
                .safeAs<CustomScriptDependenciesSource>()
    }

    open class KotlinCustomScriptModuleEntitySource(override val virtualFileUrl: VirtualFileUrl?) :
        KotlinScriptEntitySource(virtualFileUrl)
}