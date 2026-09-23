// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration

import org.jetbrains.kotlin.idea.gradle.utils.KotlinGradleModelFetchProperties
import org.jetbrains.kotlin.idea.gradleTooling.PrepareKotlinIdeImportModelProvider
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import org.jetbrains.plugins.gradle.properties.GradlePropertiesFile
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension
import java.nio.file.Path

class PrepareKotlinIdeImportTaskResolver : AbstractProjectResolverExtension() {

    override fun getToolingExtensionsClasses(): Set<Class<*>?> = PrepareKotlinIdeImportModelProvider.MODELS

    override fun getModelProvider(): ProjectImportModelProvider = PrepareKotlinIdeImportModelProvider(
        KotlinGradleModelFetchProperties.getExecutionMode(),
        isIsolatedProjectsEnabled(),
    )

    private fun isIsolatedProjectsEnabled(): Boolean {
        val project = resolverCtx.externalSystemTaskId.findProject() ?: return false
        val properties = GradlePropertiesFile.getProperties(project, Path.of(resolverCtx.projectPath))
        return properties.isolatedProjects?.value == true
    }
}
