// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.execution

import com.intellij.execution.Executor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiNameHelper
import com.intellij.task.ExecuteRunConfigurationTask
import org.jetbrains.kotlin.idea.gradleJava.run.isComposeJvm
import org.jetbrains.kotlin.idea.gradleJava.run.mainFunctionClassFqn
import org.jetbrains.plugins.gradle.execution.build.GradleExecutionEnvironmentProvider
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager

internal class ComposeJvmGradleEnvProvider : GradleExecutionEnvironmentProvider {
    override fun isApplicable(task: ExecuteRunConfigurationTask?): Boolean {
        val gradleRunConfiguration = task?.runProfile as? GradleRunConfiguration ?: return false
        return gradleRunConfiguration.isComposeJvm && !gradleRunConfiguration.mainFunctionClassFqn.isNullOrBlank()
    }

    override fun createExecutionEnvironment(
        project: Project?,
        task: ExecuteRunConfigurationTask?,
        executor: Executor?
    ): ExecutionEnvironment? {
        val gradleRunConfiguration = (task?.runProfile) as? GradleRunConfiguration ?: return null
        val mainClassFqn = gradleRunConfiguration.mainFunctionClassFqn
        if (!gradleRunConfiguration.isComposeJvm ||
            mainClassFqn.isNullOrBlank() ||
            project == null
        ) return null

        // Validate the main class FQN to prevent code injection
        if (!PsiNameHelper.getInstance(project).isQualifiedName(mainClassFqn)) {
            thisLogger().warn("Invalid main class FQN: $mainClassFqn")
            return null
        }

        val runAppTaskName = gradleRunConfiguration.name
        val initScript = generateInitScript(mainClassFqn)
        gradleRunConfiguration.putUserData<String>(GradleTaskManager.INIT_SCRIPT_KEY, initScript)
        gradleRunConfiguration.putUserData<String>(GradleTaskManager.INIT_SCRIPT_PREFIX_KEY, runAppTaskName)
        return null
    }

    private fun generateInitScript(mainClassFqn: String): String = """
        allprojects {
            afterEvaluate {
                if (extensions.findByName("compose") != null) {
                    compose.desktop.application {
                        mainClass = "$mainClassFqn"
                    }
                }
            }
        }
    """.trimIndent()
}
