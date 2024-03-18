// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties

import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.io.createDirectories
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import java.nio.file.Path
import java.util.*
import kotlin.io.path.outputStream

@TestApplication
abstract class GradlePropertiesFileTestCase {

  private lateinit var fileFixture: TempDirTestFixture

  private lateinit var externalProjectNioPath: Path
  private lateinit var gradlePropertiesNioPath: Path

  val gradlePropertiesPath get() = gradlePropertiesNioPath.toString()

  @BeforeEach
  fun setUpGradlePropertiesFileTestCase(testInfo: TestInfo) {
    fileFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture()
    fileFixture.setUp()

    val className = testInfo.testClass.get().simpleName
    val methodName = testInfo.testMethod.get().name

    externalProjectNioPath = Path.of(fileFixture.tempDirPath, className, methodName, "project")
    gradlePropertiesNioPath = externalProjectNioPath.resolve(GRADLE_PROPERTIES_FILE_NAME)

    externalProjectNioPath.createDirectories()
  }

  @AfterEach
  fun tearDownGradlePropertiesFileTestCase() {
    fileFixture.tearDown()
  }

  fun createGradlePropertiesFile(configure: Properties.() -> Unit) {
    val properties = Properties()
    properties.configure()
    properties.store(gradlePropertiesNioPath.outputStream(), null)
  }

  fun assertGradlePropertiesFile(assertion: GradleProperties. () -> Unit) {
    val properties = GradlePropertiesFile.getProperties(null, externalProjectNioPath)
    properties.assertion()
  }
}