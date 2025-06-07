// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k1

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.observation.launchTracked
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.jetbrains.kotlin.gradle.scripting.shared.getGradleVersion
import org.jetbrains.kotlin.gradle.scripting.shared.roots.GradleBuildRootsLocator
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
        val buildRootsManager: GradleBuildRootsLocator = GradleBuildRootsLocator.getInstance(project)

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

    override fun onProjectsUnlinked(linkedProjectPaths: MutableSet<String>) {
        linkedProjectPaths.forEach {
            GradleBuildRootsLocator.getInstance(project).remove(it)
        }
    }

    override fun onGradleHomeChange(oldPath: String?, newPath: String?, linkedProjectPath: String) {
        val version = GradleInstallationManager.getGradleVersion(newPath?.let { Path.of(it) })
        GradleBuildRootsLocator.getInstance(project).reloadBuildRoot(linkedProjectPath, version)
    }

    override fun onGradleDistributionTypeChange(currentValue: DistributionType?, linkedProjectPath: String) {
        GradleBuildRootsLocator.getInstance(project).reloadBuildRoot(linkedProjectPath, null)
    }
}