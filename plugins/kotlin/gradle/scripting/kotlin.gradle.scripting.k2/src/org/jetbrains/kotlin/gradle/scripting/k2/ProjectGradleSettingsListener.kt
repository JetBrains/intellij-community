// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.observation.launchTracked
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.gradle.scripting.shared.getGradleVersion
import org.jetbrains.kotlin.gradle.scripting.shared.roots.GradleBuildRootsLocator
import org.jetbrains.kotlin.idea.core.script.v1.awaitExternalSystemInitialization
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettingsListener
import java.nio.file.Path

class ProjectGradleSettingsListener(
    private val project: Project,
    private val coroutineScope: CoroutineScope
) : GradleSettingsListener {

    override fun onProjectsLinked(settings: MutableCollection<GradleProjectSettings>) {
        val buildRootsManager = GradleBuildRootsLocator.getInstance(project)
        coroutineScope.launchTracked {
            project.awaitExternalSystemInitialization()
            settings.forEach {
                val gradleVersion = getGradleVersion(project, it)
                edtWriteAction {
                    val newRoot = buildRootsManager.loadLinkedRoot(it, gradleVersion)
                    buildRootsManager.add(newRoot)
                }
            }
        }
    }

    override fun onProjectsLoaded(settings: Collection<GradleProjectSettings>) {
        val buildRootsManager = GradleBuildRootsLocator.getInstance(project)
        coroutineScope.launchTracked {
            project.awaitExternalSystemInitialization()
            settings.forEach {
                val gradleVersion = getGradleVersion(project, it)
                edtWriteAction {
                    buildRootsManager.loadLinkedRoot(it, gradleVersion)
                }
            }
        }
    }

    override fun onProjectsUnlinked(linkedProjectPaths: MutableSet<String>) {
        val buildRootsManager = GradleBuildRootsLocator.getInstance(project)

        linkedProjectPaths.forEach {
            buildRootsManager.remove(it)
        }
    }

    override fun onGradleHomeChange(oldPath: String?, newPath: String?, linkedProjectPath: String) {
        val buildRootsManager = GradleBuildRootsLocator.getInstance(project)

        val version = GradleInstallationManager.getGradleVersion(newPath?.let { Path.of(it) })
        buildRootsManager.reloadBuildRoot(linkedProjectPath, version)
    }

    override fun onGradleDistributionTypeChange(currentValue: DistributionType?, linkedProjectPath: String) {
        val buildRootsManager = GradleBuildRootsLocator.getInstance(project)
        buildRootsManager.reloadBuildRoot(linkedProjectPath, null)
    }
}