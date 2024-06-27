// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.run.gradle

import com.intellij.execution.PsiLocation
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.actions.ConfigurationFromContextImpl
import com.intellij.testFramework.assertInstanceOf
import com.intellij.testFramework.runInEdtAndWait
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.AbstractKotlinGradleCodeInsightBaseTest
import org.jetbrains.kotlin.idea.test.util.elementByOffset
import org.jetbrains.kotlin.idea.testFramework.gradle.assumeThatKotlinDslScriptsModelImportIsSupported
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.junit.jupiter.params.ParameterizedTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * @see org.jetbrains.kotlin.idea.gradleJava.run.KotlinGradleTaskRunConfigurationProducer
 */
abstract class AbstractKotlinGradleTaskRunConfigurationProducerTest : AbstractKotlinGradleCodeInsightBaseTest() {

    @ParameterizedTest
    @AllGradleVersionsSource("""
        taskName : 'task("taskName<caret>") {}',
        taskName : 'project.task("taskName<caret>") {}',
        
        taskName : 'project.tasks.register("taskName<caret>") {}',
        taskName : 'tasks.register("taskName<caret>") {}',
        taskName : 'tasks.register<Task>("taskName<caret>") {}',
        taskName : 'getTasks().register("taskName<caret>") {}',
        taskName : 'tasks.create("taskName<caret>") {}',
        taskName : 'tasks.create<Task>("taskName<caret>") {}',
        help     : 'tasks.named("help<caret>") {}',
        
        taskName : 'val task<caret>Name by tasks.registering {}',
        taskName : 'val task<caret>Name by tasks.registering(Task::class) {}',
        taskName : 'var task<caret>Name by tasks.creating {}',
        taskName : 'var task<caret>Name by tasks.creating(Task::class) {}'
    """)
    fun testTaskHasConfiguration(gradleVersion: GradleVersion, taskName: String, taskDefinition: String) {
        assumeThatKotlinDslScriptsModelImportIsSupported(gradleVersion)
        testKotlinDslEmptyProject(gradleVersion) {
            writeTextAndCommit("build.gradle.kts", taskDefinition)
            verifyConfigurationAtCaret(taskName)
        }
    }

    private fun verifyConfigurationAtCaret(taskName: String) {
        runInEdtAndWait {
            codeInsightFixture.configureFromExistingVirtualFile(getFile("build.gradle.kts"))
            val location = PsiLocation(codeInsightFixture.elementByOffset)
            val context = ConfigurationContext.createEmptyContextForLocation(location)
            val configurationFromContext = context.configurationsFromContext?.singleOrNull()
                                           ?: error("Unable to find a single configuration from context")
            verifyGradleRunConfiguration(configurationFromContext, taskName)
            verifyConfigurationProducer(configurationFromContext, context)
        }
    }

    private fun verifyGradleRunConfiguration(configurationFromContext: ConfigurationFromContext, taskName: String) {
        val gradleConfiguration = assertInstanceOf<GradleRunConfiguration>(configurationFromContext.configuration)
        assertEquals(listOf(taskName), gradleConfiguration.settings.taskNames,
                     "GradleRunConfiguration must contain only expected task name")
        assertEquals("${project.name} [$taskName]", gradleConfiguration.name)
    }

    private fun verifyConfigurationProducer(
        configurationFromContext: ConfigurationFromContext,
        context: ConfigurationContext,
    ) {
        val producer = configurationFromContext.safeAs<ConfigurationFromContextImpl>()?.configurationProducer
                       ?: error("Unable to find a RunConfigurationProducer for configuration")
        assertTrue(producer.isConfigurationFromContext(configurationFromContext.configuration, context),
                   "Producer must be able to identify a configuration that was created by it")
    }
}
