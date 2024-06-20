// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.run.gradle

import com.intellij.testFramework.assertInstanceOf
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.AbstractKotlinGradleCodeInsightBaseTest
import org.jetbrains.kotlin.idea.gradleJava.scripting.kotlinDslScriptsModelImportSupported
import org.jetbrains.kotlin.idea.run.getConfiguration
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
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
        taskName : 'project.tasks.register("taskName") {}',
        taskName : 'tasks.register("taskName") {}',
        taskName : 'getTasks().register("taskName") {}',
        taskName : 'project.task("taskName") {}',
        taskName : 'task("taskName") {}',
        taskName : 'tasks.create("taskName") {}',
        help     : 'tasks.named("help") {}'
    """)
    fun testTaskHasConfiguration(gradleVersion: GradleVersion, taskName: String, taskDefinition: String) {
        assumeThatKotlinDslScriptsModelImportIsSupported(gradleVersion)
        testKotlinDslEmptyProject(gradleVersion) {
            writeTextAndCommit("build.gradle.kts", taskDefinition)
            runReadActionAndWait {
                val buildFile = getFile("build.gradle.kts")
                val configurationFromContext = getConfiguration(buildFile, project, "\"$taskName\"")
                val taskConfiguration = assertInstanceOf<GradleRunConfiguration>(configurationFromContext.configuration)
                assertEquals(listOf(taskName), taskConfiguration.settings.taskNames)
                assertEquals("${project.name} [$taskName]", taskConfiguration.name)
            }
        }
    }

    companion object {
        private fun assumeThatKotlinDslScriptsModelImportIsSupported(gradleVersion: GradleVersion) {
            Assumptions.assumeTrue(kotlinDslScriptsModelImportSupported(gradleVersion.version)) {
                "Gradle ${gradleVersion.version} doesn't support Kotlin DSL Scripts Model import."
            }
        }
    }
}