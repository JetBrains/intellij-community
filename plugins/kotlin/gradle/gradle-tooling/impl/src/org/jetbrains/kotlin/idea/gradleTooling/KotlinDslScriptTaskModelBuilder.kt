// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleTooling

import org.gradle.api.Project
import org.gradle.api.logging.Logging
import org.gradle.tooling.model.kotlin.dsl.KotlinDslModelsParameters.PREPARATION_TASK_NAME
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinExtensionReflection
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext

class KotlinDslScriptTaskModelBuilder : AbstractModelBuilderService() {

    companion object {
        val logger = Logging.getLogger(KotlinDslScriptTaskModelBuilder::class.java)
    }
    override fun canBuild(modelName: String): Boolean {
        return KotlinDslScriptAdditionalTask::class.java.name == modelName
    }

    override fun buildAll(modelName: String, project: Project, context: ModelBuilderContext): Any? {
        if (kotlinDslScriptsModelImportSupported(project.gradle.gradleVersion)) {
            val startParameter = project.gradle.startParameter

            val tasks = HashSet(startParameter.taskNames)
            tasks.add(PREPARATION_TASK_NAME)
            startParameter.setTaskNames(tasks)
        }
        return null
    }

    override fun getErrorMessageBuilder(project: Project, e: Exception): ErrorMessageBuilder {
        return ErrorMessageBuilder.create(
            project, e, "Kotlin DSL script model errors"
        ).withDescription("Unable to set $PREPARATION_TASK_NAME sync task.")
    }

    private fun kotlinDslScriptsModelImportSupported(currentGradleVersion: String): Boolean {
        return GradleVersion.version(currentGradleVersion) >= GradleVersion.version("6.0")
    }

    internal fun KotlinExtensionReflection.parseKotlinGradlePluginVersion(): KotlinGradlePluginVersion? {
        val version = KotlinGradlePluginVersion.parse(kotlinGradlePluginVersion ?: return null)
        if (version == null) {
            logger.warn("[sync warning] Failed to parse KotlinGradlePluginVersion: version == null")
        }
        return version
    }

}