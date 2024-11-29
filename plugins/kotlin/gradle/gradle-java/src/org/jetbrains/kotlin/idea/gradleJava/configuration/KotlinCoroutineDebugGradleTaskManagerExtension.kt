// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.configuration

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import kotlinx.coroutines.DEBUG_PROPERTY_NAME
import kotlinx.coroutines.DEBUG_PROPERTY_VALUE_OFF
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.extensions.KotlinJvmDebuggerFacade
import org.jetbrains.plugins.gradle.service.execution.GRADLE_TOOLING_EXTENSION_CLASSES
import org.jetbrains.plugins.gradle.service.execution.joinInitScripts
import org.jetbrains.plugins.gradle.service.execution.loadInitScript
import org.jetbrains.plugins.gradle.service.execution.loadToolingExtensionProvidingInitScript
import org.jetbrains.plugins.gradle.service.execution.toGroovyStringLiteral
import org.jetbrains.plugins.gradle.service.task.GradleTaskManagerExtension
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings

class KotlinCoroutineDebugGradleTaskManagerExtension : GradleTaskManagerExtension {

    companion object {

        private val LOG = Logger.getInstance(this::class.java)

        private const val MIN_SUPPORTED_GRADLE_VERSION = "4.6" // CommandLineArgumentProvider is available only since Gradle 4.6

        private const val KOTLIN_COROUTINE_DEBUG_SCRIPT_NAME = "ijKotlinCoroutineJvmDebugInit"
    }

    override fun configureTasks(
        projectPath: String,
        id: ExternalSystemTaskId,
        settings: GradleExecutionSettings,
        gradleVersion: GradleVersion?
    ) {
        try {
            if (KotlinJvmDebuggerFacade.instance?.isCoroutineAgentAllowedInDebug == true) {
                if (gradleVersion != null && GradleVersionUtil.isGradleAtLeast(gradleVersion, MIN_SUPPORTED_GRADLE_VERSION)) {
                    val initScript = joinInitScripts(
                        loadToolingExtensionProvidingInitScript(GRADLE_TOOLING_EXTENSION_CLASSES),
                        loadInitScript(
                            javaClass, "/org/jetbrains/kotlin/idea/gradle/debugging/KotlinCoroutineJvmDebugInit.gradle", mapOf(
                                "DEBUG_PROPERTY_NAME" to DEBUG_PROPERTY_NAME.toGroovyStringLiteral(),
                                "DEBUG_PROPERTY_VALUE_OFF" to DEBUG_PROPERTY_VALUE_OFF.toGroovyStringLiteral(),
                            )
                        )
                    )
                    settings.addInitScript(KOTLIN_COROUTINE_DEBUG_SCRIPT_NAME, initScript)
                }
            }
        } catch (e: Exception) {
            LOG.error("Gradle: not possible to attach a coroutine debugger agent.", e)
        }
    }
}
