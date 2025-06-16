// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties

import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.gradle.util.GradleConstants.GRADLE_DAEMON_JVM_PROPERTIES_FILE_NAME
import org.jetbrains.plugins.gradle.util.GradleConstants.GRADLE_DIR_NAME
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.createParentDirectories
import kotlin.io.path.outputStream

@TestApplication
abstract class GradleDaemonJvmPropertiesFileTestCase {

  @TempDir
  private lateinit var rootPath: Path

  val projectPath: Path
    get() = rootPath.resolve("project")

  val projectPropertiesPath: Path
    get() = projectPath.resolve(Paths.get(GRADLE_DIR_NAME, GRADLE_DAEMON_JVM_PROPERTIES_FILE_NAME))

  fun createGradleDaemonJvmPropertiesFile(configure: Properties.() -> Unit) {
    val properties = Properties()
    properties.configure()
    projectPropertiesPath.createParentDirectories()
    properties.store(projectPropertiesPath.outputStream(), null)
  }

  fun assertGradleDaemonJvmPropertiesFile(assertion: GradleDaemonJvmProperties. () -> Unit) {
    GradleDaemonJvmPropertiesFile.getProperties(projectPath).assertion()
  }
}