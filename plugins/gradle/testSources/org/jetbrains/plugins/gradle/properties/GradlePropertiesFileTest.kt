// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.LightPlatformTestCase
import org.junit.Test
import java.io.File
import java.util.*
import kotlin.io.path.Path

class GradlePropertiesFileTest : LightPlatformTestCase() {

  private val projectPath by lazy { project.basePath.orEmpty() }
  private val externalProjectPath by lazy { Path(projectPath) }
  private val gradlePropertiesPath by lazy { externalProjectPath.resolve(GRADLE_PROPERTIES_FILE_NAME).toString() }

  override fun tearDown() {
    super.tearDown()
    closeAndDeleteProject()
  }

  @Test
  fun testDefaultGradleProperties() {
    val gradleProperties = GradleProperties.EMPTY
    assertNull(gradleProperties.javaHomeProperty)
    assertNull(gradleProperties.gradleLoggingLevel)
  }

  @Test
  fun testNotPresentProjectGradlePropertiesFile() {
    GradlePropertiesFile.getProperties(project, externalProjectPath).run {
      assertEquals(GradleProperties.EMPTY, this)
    }
  }

  @Test
  fun testEmptyProjectGradlePropertiesFile() = testGradleProperties(
    assertion = {
      GradlePropertiesFile.getProperties(project, externalProjectPath).run {
        assertNull(javaHomeProperty)
        assertNull(gradleLoggingLevel)
      }
    }
  )

  @Test
  fun testUnexpectedPropertiesInProjectGradlePropertiesFile() = testGradleProperties(
    configure = {
      setProperty("another.property.1", "value1")
      setProperty("another.property.2", "value2")
    },
    assertion = {
      GradlePropertiesFile.getProperties(project, externalProjectPath).run {
        assertNull(javaHomeProperty)
        assertNull(gradleLoggingLevel)
      }
    }
  )

  @Test
  fun testExpectedPropertiesInProjectGradlePropertiesFile() = testGradleProperties(
    configure = {
      setProperty(GRADLE_JAVA_HOME_PROPERTY, "javaHome")
      setProperty(GRADLE_LOGGING_LEVEL_PROPERTY, "info")
    },
    assertion = {
      GradlePropertiesFile.getProperties(project, externalProjectPath).run {
        assertEquals("javaHome", javaHomeProperty?.value)
        assertEquals(gradlePropertiesPath, javaHomeProperty?.location)
        assertEquals("info", gradleLoggingLevel?.value)
        assertEquals(gradlePropertiesPath, gradleLoggingLevel?.location)
      }
    }
  )

  @Test
  fun testMultiplePropertiesInProjectGradlePropertiesFile() = testGradleProperties(
    configure = {
      setProperty("another.property.1", "value1")
      setProperty(GRADLE_JAVA_HOME_PROPERTY, "value2")
      setProperty(GRADLE_LOGGING_LEVEL_PROPERTY, "value3")
      setProperty("another.property.2", "value4")
    },
    assertion = {
      GradlePropertiesFile.getProperties(project, externalProjectPath).run {
        assertEquals("value2", javaHomeProperty?.value)
        assertEquals(gradlePropertiesPath, javaHomeProperty?.location)
        assertEquals("value3", gradleLoggingLevel?.value)
        assertEquals(gradlePropertiesPath, gradleLoggingLevel?.location)
      }
    }
  )

  private fun testGradleProperties(
    configure: Properties.() -> Unit = {},
    assertion: () -> Unit
  ) {
    val gradlePropertiesFile = File(gradlePropertiesPath)
    FileUtil.createIfNotExists(gradlePropertiesFile)
    gradlePropertiesFile.outputStream().use {
      Properties().run {
        configure()
        store(it, null)
      }
    }
    assertion()
  }
}