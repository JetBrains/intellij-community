// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.execution

import com.intellij.execution.Executor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiNameHelper
import com.intellij.task.ExecuteRunConfigurationTask
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.gradleJava.run.isComposeGradlePluginConfigured
import org.jetbrains.kotlin.idea.gradleJava.run.mainFunctionClassFqn
import org.jetbrains.plugins.gradle.execution.build.GradleExecutionEnvironmentProvider
import org.jetbrains.plugins.gradle.service.execution.GRADLE_TOOLING_EXTENSION_CLASSES
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.service.execution.joinInitScripts
import org.jetbrains.plugins.gradle.service.execution.loadToolingExtensionProvidingInitScript
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager

internal class ComposeJvmGradleEnvProvider : GradleExecutionEnvironmentProvider {
    override fun isApplicable(task: ExecuteRunConfigurationTask?): Boolean {
        val gradleRunConfiguration = task?.runProfile as? GradleRunConfiguration ?: return false
        return gradleRunConfiguration.isComposeGradlePluginConfigured && !gradleRunConfiguration.mainFunctionClassFqn.isNullOrBlank()
    }

    override fun createExecutionEnvironment(
        project: Project?,
        task: ExecuteRunConfigurationTask?,
        executor: Executor?
    ): ExecutionEnvironment? {
        val gradleRunConfiguration = (task?.runProfile) as? GradleRunConfiguration ?: return null
        val mainClassFqn = gradleRunConfiguration.mainFunctionClassFqn
        if (!gradleRunConfiguration.isComposeGradlePluginConfigured ||
            mainClassFqn.isNullOrBlank() ||
            project == null
        ) return null

        // Validate the main class FQN to prevent code injection
        if (!PsiNameHelper.getInstance(project).isQualifiedName(mainClassFqn)) {
            thisLogger().warn("Invalid main class FQN: $mainClassFqn")
            return null
        }

        val runAppTaskName = gradleRunConfiguration.name
        val initScript = joinInitScripts(
            // The application script calls GradleLifecycleUtil, so the Gradle daemon needs the tooling extension classes.
            loadToolingExtensionProvidingInitScript(GRADLE_TOOLING_EXTENSION_CLASSES),
            generateComposeApplicationScript(mainClassFqn)
        )
        gradleRunConfiguration.putUserData<String>(GradleTaskManager.INIT_SCRIPT_KEY, initScript)
        gradleRunConfiguration.putUserData<String>(GradleTaskManager.INIT_SCRIPT_PREFIX_KEY, runAppTaskName)
        return null
    }
}

/**
 * Builds the init script that sets the Compose Desktop main class to [mainClassFqn].
 *
 * The script asks `GradleLifecycleUtil` to configure each project.
 * A top level `allprojects` call in an init script makes the root project configure every other project.
 * Gradle rejects that cross-project access when the build turns on Isolated Projects.
 */
@VisibleForTesting
internal fun generateComposeApplicationScript(mainClassFqn: String): String = """
    import com.intellij.gradle.toolingExtension.impl.initScript.util.GradleLifecycleUtil

    GradleLifecycleUtil.afterProject(gradle) { Project project ->
        if (project.extensions.findByName("compose") != null) {
            project.compose.desktop.application {
                mainClass = '$mainClassFqn'
            }
        }
    }
""".trimIndent()
