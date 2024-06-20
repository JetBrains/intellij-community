// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.run.gradle

import com.intellij.openapi.Disposable
import com.intellij.testFramework.assertInstanceOf
import com.intellij.testFramework.junit5.TestDisposable
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.AbstractKotlinGradleCodeInsightBaseTest
import org.jetbrains.kotlin.idea.gradleJava.scripting.kotlinDslScriptsModelImportSupported
import org.jetbrains.kotlin.idea.run.getConfiguration
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.jetbrains.plugins.gradle.util.runReadActionAndWait
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.params.ParameterizedTest
import kotlin.test.assertEquals

/**
 * @see org.jetbrains.kotlin.idea.gradleJava.run.KotlinGradleTaskRunConfigurationProducer
 */
abstract class AbstractKotlinGradleTaskRunConfigurationProducerTest : AbstractKotlinGradleCodeInsightBaseTest() {

    @ParameterizedTest
    @AllGradleVersionsSource("""
        "project_tasks_register_name",
        "tasks_register_name",
        "getTasks_register_name",
        "project_task_name",
        "project_delegate_task_name",
        "tasks_create_name",
        "help"
    """
    )
    fun testTaskHasConfiguration(gradleVersion: GradleVersion, taskName: String) {
        assumeThatKotlinDslScriptsModelImportIsSupported(gradleVersion)
        test(gradleVersion, FIXTURE_WITH_TASKS) {
            runReadActionAndWait {
                val buildFile = getFile("build.gradle.kts")
                val configurationFromContext = getConfiguration(buildFile, project, "\"$taskName\"")
                val taskConfiguration = assertInstanceOf<GradleRunConfiguration>(configurationFromContext.configuration)
                assertEquals(listOf(taskName), taskConfiguration.settings.taskNames)
                assertEquals("$PROJECT_NAME [$taskName]", taskConfiguration.name)
            }
        }
    }

    companion object {
        const val PROJECT_NAME = "kotlin-script-tasks"
        private val FIXTURE_WITH_TASKS: GradleTestFixtureBuilder = GradleTestFixtureBuilder.create(PROJECT_NAME) {
            withSettingsFile(useKotlinDsl = true) {
                setProjectName(PROJECT_NAME)
            }
            withBuildFile(
                useKotlinDsl = true,
                content = """
                    project.tasks.register("project_tasks_register_name") {}
                    tasks.register("tasks_register_name") {}
                    getTasks().register("getTasks_register_name") {}
            
                    project.task("project_task_name") {}
                    task("project_delegate_task_name") {}
                    
                    tasks.create("tasks_create_name") {}
                    tasks.named("help") {}
                """.trimIndent()
            )
        }

        private fun assumeThatKotlinDslScriptsModelImportIsSupported(gradleVersion: GradleVersion) {
            Assumptions.assumeTrue(kotlinDslScriptsModelImportSupported(gradleVersion.version)) {
                "Gradle ${gradleVersion.version} doesn't support Kotlin DSL Scripts Model import."
            }
        }
    }
}