// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.testing

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.util.registry.Registry
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.execution.joinInitScripts
import org.jetbrains.plugins.gradle.service.execution.loadInitScript
import org.jetbrains.plugins.gradle.service.task.GradleTaskManagerExtension
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings

class KotlinTestGradleTaskManagerExtension : GradleTaskManagerExtension {

    override fun configureTasks(
        projectPath: String,
        id: ExternalSystemTaskId,
        settings: GradleExecutionSettings,
        gradleVersion: GradleVersion?
    ) {
        if (!Registry.`is`(KotlinTestTasksResolver.ENABLED_REGISTRY_KEY)) {
            return
        }

        if (settings.isRunAsTest && !settings.isBuiltInTestEventsUsed) {
            try {
                settings.addInitScript(
                    KOTLIN_TEST_LOGGER_SCRIPT_NAME, joinInitScripts(
                        loadInitScript(javaClass, "/org/jetbrains/kotlin/idea/gradle/testing/KotlinMppTestLogger.groovy"),
                        loadInitScript(javaClass, "/org/jetbrains/kotlin/idea/gradle/testing/addKotlinMppTestListener.groovy"),
                    )
                )
            } catch (e: Exception) {
                LOG.error(e)
            }
        }
    }

    companion object {

        private const val KOTLIN_TEST_LOGGER_SCRIPT_NAME = "ijKotlinTestLogger"

        private val LOG by lazy { Logger.getInstance(KotlinTestTasksResolver::class.java) }
    }
}