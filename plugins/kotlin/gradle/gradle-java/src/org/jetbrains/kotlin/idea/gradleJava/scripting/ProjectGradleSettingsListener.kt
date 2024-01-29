// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.scripting

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.core.script.ScriptModel
import org.jetbrains.kotlin.idea.core.script.configureGradleScriptsK2
import org.jetbrains.kotlin.idea.core.script.k2ScriptingEnabled
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
                val newRoot = buildRootsManager.loadLinkedRoot(it)

                if (newRoot is Imported && k2ScriptingEnabled()) {
                    val definitions = loadGradleDefinitions(it.externalProjectPath, newRoot.data.gradleHome, newRoot.data.javaHome, project)
                    val scripts = newRoot.data.models.mapNotNull {
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
                    configureGradleScriptsK2(newRoot.data.javaHome, project, scripts, definitions)
                }

                buildRootsManager.add(newRoot)
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
}