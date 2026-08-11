// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.scripting.shared

import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import org.gradle.tooling.model.kotlin.dsl.KotlinDslModelsParameters
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.scripting.shared.importing.KotlinDslScriptModelResolver
import org.jetbrains.kotlin.gradle.scripting.shared.roots.GradleBuildRoot
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.service.project.GradlePartialResolverPolicy
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.util.GradleConstants

fun runPartialGradleImport(project: Project, root: GradleBuildRoot) {
    if (root.isImportingInProgress()) return

    ExternalSystemUtil.refreshProject(
        root.externalProjectPath,
        ImportSpecBuilder(project, GradleConstants.SYSTEM_ID)
            .withVmOptions(
                "-D${KotlinDslModelsParameters.PROVIDER_MODE_SYSTEM_PROPERTY_NAME}=" +
                        KotlinDslModelsParameters.CLASSPATH_MODE_SYSTEM_PROPERTY_VALUE
            )
            .projectResolverPolicy(
                GradlePartialResolverPolicy { it is KotlinDslScriptModelResolver }
            )
    )
}

fun getGradleVersion(project: Project, settings: GradleProjectSettings): String {
    val gradleHome = service<GradleInstallationManager>().getGradleHomePath(project, settings.externalProjectPath)
    return GradleInstallationManager.getGradleVersion(gradleHome) ?: GradleVersion.current().version
}
