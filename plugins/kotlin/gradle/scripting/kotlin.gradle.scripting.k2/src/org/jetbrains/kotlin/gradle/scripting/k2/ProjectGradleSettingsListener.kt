// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.observation.launchTracked
import com.intellij.psi.PsiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.jetbrains.kotlin.gradle.scripting.shared.GradleScriptModel
import org.jetbrains.kotlin.gradle.scripting.shared.GradleScriptRefinedConfigurationProvider
import org.jetbrains.kotlin.gradle.scripting.shared.getGradleVersion
import org.jetbrains.kotlin.gradle.scripting.shared.loadGradleDefinitions
import org.jetbrains.kotlin.gradle.scripting.shared.roots.GradleBuildRootData
import org.jetbrains.kotlin.gradle.scripting.shared.roots.GradleBuildRootsManager
import org.jetbrains.kotlin.gradle.scripting.shared.roots.Imported
import org.jetbrains.kotlin.idea.core.script.k2.DefaultScriptResolutionStrategy
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettingsListener
import java.nio.file.Path

class ProjectGradleSettingsListener(
    private val project: Project,
    private val coroutineScope: CoroutineScope
) : GradleSettingsListener {

    private val buildRootsManager: GradleBuildRootsManager = GradleBuildRootsManager.getInstanceSafe(project)

    override fun onProjectsLinked(settings: MutableCollection<GradleProjectSettings>) {
        settings.forEach {
            coroutineScope.launchTracked(Dispatchers.IO) {
                val gradleVersion = getGradleVersion(project, it)
                edtWriteAction {
                    val newRoot = buildRootsManager.loadLinkedRoot(it, gradleVersion)
                    buildRootsManager.add(newRoot)
                }
            }
        }
    }

    override fun onProjectsLoaded(settings: Collection<GradleProjectSettings>) {
        settings.forEach {
            coroutineScope.launchTracked(Dispatchers.IO) {
                val gradleVersion = getGradleVersion(project, it)
                val newRoot = edtWriteAction {
                    buildRootsManager.loadLinkedRoot(it, gradleVersion)
                }
                if (newRoot is Imported) {
                    loadScriptConfigurations(newRoot.data, it)
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
        val version = GradleInstallationManager.getGradleVersion(newPath?.let { Path.of(it) })
        buildRootsManager.reloadBuildRoot(linkedProjectPath, version)
    }

    override fun onGradleDistributionTypeChange(currentValue: DistributionType?, linkedProjectPath: String) {
        buildRootsManager.reloadBuildRoot(linkedProjectPath, null)
    }

    private suspend fun loadScriptConfigurations(
        data: GradleBuildRootData,
        settings: GradleProjectSettings
    ) {
        if (data.models.isEmpty()) return
        val javaHome = data.javaHome
        val definitions = loadGradleDefinitions(settings.externalProjectPath, data.gradleHome, javaHome, project)

        val gradleScripts = data.models.mapNotNullTo(mutableSetOf()) {
            val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(Path.of(it.file)) ?: return@mapNotNullTo null
            GradleScriptModel(
                virtualFile,
                it.classPath,
                it.sourcePath,
                it.imports,
                javaHome
            )
        }

        GradleScriptDefinitionsHolder.getInstance(project).updateDefinitions(definitions)
        GradleScriptRefinedConfigurationProvider.getInstance(project).processScripts(gradleScripts)

        val ktFiles = gradleScripts.mapNotNull {
            readAction { PsiManager.getInstance(project).findFile(it.virtualFile) as? KtFile }
        }.toTypedArray()

        DefaultScriptResolutionStrategy.getInstance(project).execute(*ktFiles).join()
    }
}