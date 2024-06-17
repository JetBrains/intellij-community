// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.scripting

import com.intellij.openapi.application.readAndWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.core.script.ScriptModel
import org.jetbrains.kotlin.idea.core.script.configureGradleScriptsK2
import org.jetbrains.kotlin.idea.gradleJava.loadGradleDefinitions
import org.jetbrains.kotlin.idea.gradleJava.scripting.roots.GradleBuildRootsManager
import org.jetbrains.kotlin.idea.gradleJava.scripting.roots.Imported
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettingsListener
import java.nio.file.Paths

class ProjectGradleSettingsListener(val project: Project, private val cs: CoroutineScope) : GradleSettingsListener {

    private val buildRootsManager = GradleBuildRootsManager.getInstanceSafe(project)

    override fun onProjectsLinked(settings: MutableCollection<GradleProjectSettings>) {
        settings.forEach {
            cs.launch(Dispatchers.IO) {
                readAndWriteAction {
                    val newRoot = buildRootsManager.loadLinkedRoot(it)

                    writeAction {
                        buildRootsManager.add(newRoot)

                        if (newRoot is Imported && KotlinPluginModeProvider.isK2Mode()) {
                            launch { loadScriptConfigurations(newRoot, it) }
                        }
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
        GradleScriptDefinitionsSource.getInstance(project)?.updateDefinitions(definitions)

        val scripts = root.data.models.mapNotNull {
            val path = Paths.get(it.file)
            VirtualFileManager.getInstance().findFileByNioPath(path)?.let { virtualFile ->
                ScriptModel(
                    virtualFile,
                    it.classPath,
                    it.sourcePath,
                    it.imports
                )
            }
        }.toSet()

        configureGradleScriptsK2(root.data.javaHome, project, scripts)
    }
}