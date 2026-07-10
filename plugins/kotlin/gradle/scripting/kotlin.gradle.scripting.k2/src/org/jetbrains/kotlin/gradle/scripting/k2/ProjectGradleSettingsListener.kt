// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.gradle.scripting.shared.roots.GradleBuildRootsLocator
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettingsListener

class ProjectGradleSettingsListener(
    private val project: Project,
) : GradleSettingsListener {

    override fun onProjectsLinked(settings: MutableCollection<GradleProjectSettings>) {
        settings.forEach { updateNotifications(it.externalProjectPath) }
    }

    override fun onProjectsLoaded(settings: Collection<GradleProjectSettings>) {
        settings.forEach { updateNotifications(it.externalProjectPath) }
    }

    override fun onProjectsUnlinked(linkedProjectPaths: MutableSet<String>) {
        val buildRootsManager = GradleBuildRootsLocator.getInstance(project)

        linkedProjectPaths.forEach {
            buildRootsManager.remove(it)
        }
    }

    override fun onGradleHomeChange(oldPath: String?, newPath: String?, linkedProjectPath: String) {
        updateNotifications(linkedProjectPath)
    }

    override fun onGradleDistributionTypeChange(currentValue: DistributionType?, linkedProjectPath: String) {
        updateNotifications(linkedProjectPath)
    }

    private fun updateNotifications(linkedProjectPath: String) {
        GradleBuildRootsLocator.getInstance(project).updateNotifications { it.startsWith(linkedProjectPath) }
    }
}