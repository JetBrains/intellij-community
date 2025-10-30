// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.run.gradle

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatKotlinDslScriptsModelImportIsSupported
import org.junit.jupiter.params.ParameterizedTest

/**
 * @see org.jetbrains.kotlin.idea.gradleJava.run.KotlinGradleTaskRunConfigurationProducer
 */
abstract class AbstractKotlinGradleTaskRunConfigurationProducerTest : AbstractKotlinGradleRunConfigurationProducerBaseTest() {

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
        
        taskName : 'val task<caret>Name by tasks.registering',
        taskName : 'val task<caret>Name by tasks.registering {}',
        taskName : 'val task<caret>Name by tasks.registering(Task::class) {}',
        taskName : 'var task<caret>Name by tasks.creating',
        taskName : 'var task<caret>Name by tasks.creating {}',
        taskName : 'var task<caret>Name by tasks.creating(Task::class) {}'
    """)
    fun testTaskHasConfiguration(gradleVersion: GradleVersion, taskName: String, taskDefinition: String) {
        assumeThatKotlinDslScriptsModelImportIsSupported(gradleVersion)
        testKotlinDslEmptyProject(gradleVersion) {
            writeTextAndCommit("build.gradle.kts", taskDefinition)
            verifyGradleConfigurationAtCaret(taskName)
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource("""
        'getTasks().register("taskName") {
            doLast { println("task<caret>Name") }
        }',
        'var taskName by tasks.registering {
            doLast { println("task<caret>Name") }
        }' 
    """)
    fun testOtherLineDontHaveConfiguration(gradleVersion: GradleVersion, taskDefinition: String) {
        assumeThatKotlinDslScriptsModelImportIsSupported(gradleVersion)
        testKotlinDslEmptyProject(gradleVersion) {
            writeTextAndCommit("build.gradle.kts", taskDefinition)
            assertNoConfigurationAtCaret()
        }
    }
}
