// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.io.createDirectories
import org.jetbrains.plugins.gradle.util.GradleConstants.GRADLE_PROPERTIES_FILE_NAME
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.*
import kotlin.io.path.outputStream

@TestApplication
abstract class GradlePropertiesFileTestCase {

  @TempDir
  private lateinit var rootPath: Path

  val projectPath: Path
    get() = rootPath.resolve("project")

  val projectPropertiesPath: Path
    get() = projectPath.resolve(GRADLE_PROPERTIES_FILE_NAME)

  fun createGradlePropertiesFile(projectPath: Path, configure: Properties.() -> Unit) {
    val properties = Properties()
    properties.configure()
    projectPath.createDirectories()
    properties.store(projectPath.resolve(GRADLE_PROPERTIES_FILE_NAME).outputStream(), null)
  }

  fun assertGradlePropertiesFile(projectPath: Path, assertion: GradleProperties. () -> Unit) {
    GradlePropertiesFile.getProperties(null, projectPath).assertion()
  }
}