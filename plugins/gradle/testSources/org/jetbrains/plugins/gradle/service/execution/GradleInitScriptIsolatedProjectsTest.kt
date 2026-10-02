// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Checks that the init scripts of the Gradle plugin support Isolated Projects.
 *
 * In an init script the closure delegate is the `Gradle` object.
 * A top level `allprojects` call therefore makes the root project configure every other project.
 * Gradle 9.7 rejects that cross-project access and fails the build.
 * `GradleLifecycleUtil` lets each project configure itself instead.
 *
 * See IDEA-392539 and IDEA-363895.
 */
@TestApplication
class GradleInitScriptIsolatedProjectsTest {

  @Test
  fun `the main init script configures each project through the Gradle lifecycle`() {
    val scripts = createMainInitScript(isBuildSrcProject = true, toolingExtensionClasses = emptySet())
    assertFalse(scripts.isEmpty(), "The main init script has no variant to check")
    for (script in scripts) {
      assertUsesGradleLifecycle(script.script)
    }
  }

  @Test
  fun `the task init script configures each project through the Gradle lifecycle`() {
    val script = loadTaskInitScript(
      projectPath = ":app",
      taskName = "ijTask",
      taskType = "JavaExec",
      toolingExtensionClasses = emptySet(),
      taskConfiguration = null,
    )
    assertUsesGradleLifecycle(script)
  }

  @Test
  fun `the application init script configures each project through the Gradle lifecycle`() {
    val script = loadApplicationInitScript(
      gradlePath = ":app",
      runAppTaskName = "App.main()",
      mainClassToRun = "my.app.App",
      javaExePath = "/jdk/bin/java",
      sourceSetName = "main",
      params = null,
      definitions = null,
      intelliJRtPath = null,
      workingDirectory = null,
      useManifestJar = false,
      useArgsFile = false,
      useClasspathFile = false,
      javaModuleName = null,
    )
    assertUsesGradleLifecycle(script)
  }

  private fun assertUsesGradleLifecycle(script: String) {
    assertTrue(script.contains("GradleLifecycleUtil.")) { "The init script must call GradleLifecycleUtil:\n$script" }
    assertFalse(script.contains("allprojects")) { "Isolated Projects rejects 'allprojects' in an init script:\n$script" }
  }
}
