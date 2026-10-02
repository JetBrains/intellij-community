// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.execution

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks that the init scripts of the Kotlin run configurations support Isolated Projects.
 *
 * In an init script the closure delegate is the `Gradle` object.
 * A top level `allprojects` call therefore makes the root project configure every other project.
 * Gradle 9.7 rejects that cross-project access and fails the build.
 * `GradleLifecycleUtil` lets each project configure itself instead.
 *
 * See IDEA-392539. The same fix for the Java run configurations is IDEA-363895.
 */
class KotlinGradleInitScriptTest {

    @Test
    fun `the application task script lets the target project configure itself`() {
        val script = generateApplicationTaskScript(
            gradleProjectId = "myProject:app",
            runAppTaskName = "MainKt.main()",
            mainClass = "my.app.MainKt",
            javaExePath = "/jdk/bin/java",
            workingDirectory = "/myProject/app",
            sourceSetName = "main",
            taskParams = "args 'first'",
        )

        assertNoCrossProjectAccess(script)
        assertTrue(
            "The script must register the task through GradleLifecycleUtil:\n$script",
            script.contains("GradleLifecycleUtil.afterProject(gradle) { Project project ->")
        )
    }

    @Test
    fun `the compose application script lets the target project configure itself`() {
        val script = generateComposeApplicationScript("my.app.MainKt")

        assertNoCrossProjectAccess(script)
        assertTrue(
            "The script must set the main class through GradleLifecycleUtil:\n$script",
            script.contains("GradleLifecycleUtil.afterProject(gradle) { Project project ->")
        )
    }

    private fun assertNoCrossProjectAccess(script: String) {
        assertTrue(
            "The script must import GradleLifecycleUtil:\n$script",
            script.contains("import com.intellij.gradle.toolingExtension.impl.initScript.util.GradleLifecycleUtil")
        )
        assertFalse(
            "Isolated Projects rejects 'allprojects' in an init script:\n$script",
            script.contains("allprojects")
        )
        assertFalse(
            "Isolated Projects rejects 'afterEvaluate' on another project:\n$script",
            script.contains("afterEvaluate")
        )
    }
}
