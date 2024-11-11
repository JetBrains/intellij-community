// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.scripting

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.observation.launchTracked
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.core.script.scriptConfigurationsSourceOfType
import org.jetbrains.kotlin.idea.core.script.scriptDefinitionsSourceOfType
import org.jetbrains.kotlin.idea.gradleJava.loadGradleDefinitions
import org.jetbrains.kotlin.idea.gradleJava.scripting.roots.GradleBuildRootsManager
import org.jetbrains.kotlin.idea.gradleJava.scripting.roots.Imported
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettingsListener
import java.nio.file.Paths

class ProjectGradleSettingsListener(
    private val project: Project,
    private val coroutineScope: CoroutineScope
) : GradleSettingsListener {

    private val buildRootsManager: GradleBuildRootsManager = GradleBuildRootsManager.getInstanceSafe(project)

    override fun onProjectsLinked(settings: MutableCollection<GradleProjectSettings>) {
        settings.forEach {
            coroutineScope.launchTracked(Dispatchers.IO) {
                writeAction {
                    val newRoot = buildRootsManager.loadLinkedRoot(it)
                    buildRootsManager.add(newRoot)
                }
            }
        }
    }

    override fun onProjectsLoaded(settings: Collection<GradleProjectSettings>) {
        if (KotlinPluginModeProvider.isK2Mode()) {
            settings.forEach {
                coroutineScope.launchTracked(Dispatchers.IO) {
                    val newRoot = writeAction {
                        buildRootsManager.loadLinkedRoot(it)
                    }
                    if (newRoot is Imported) {
                        loadScriptConfigurations(newRoot, it)
                    }
                }
            }
        }
    }

    override fun onProjectsUnlinked(linkedProjectPaths: MutableSet<String>) {
        linkedProjectPaths.forEach {
            buildRootsManager.remove(it)
        }
    }

    override fun onGradleHomeChange(oldPath: String?, newPath: String?, linkedProjectPath: String) {
        val version = GradleInstallationManager.getGradleVersion(newPath)
        buildRootsManager.reloadBuildRoot(linkedProjectPath, version)
    }

    override fun onGradleDistributionTypeChange(currentValue: DistributionType?, linkedProjectPath: String) {
        buildRootsManager.reloadBuildRoot(linkedProjectPath, null)
    }

    private suspend fun loadScriptConfigurations(
        root: Imported,
        settings: GradleProjectSettings
    ) {
        val definitions = loadGradleDefinitions(settings.externalProjectPath, root.data.gradleHome, root.data.javaHome, project)

        val gradleScripts = root.data.models.mapNotNull {
            val path = Paths.get(it.file)
            VirtualFileManager.getInstance().findFileByNioPath(path)?.let { virtualFile ->
                GradleScriptModel(
                    virtualFile,
                    it.classPath,
                    it.sourcePath,
                    it.imports,
                    root.data.javaHome
                )
            }
        }.toSet()

        project.scriptDefinitionsSourceOfType<GradleScriptDefinitionsSource>()?.updateDefinitions(definitions)
        project.scriptConfigurationsSourceOfType<GradleScriptConfigurationsSource>()?.updateDependenciesAndCreateModules(gradleScripts)
    }
}