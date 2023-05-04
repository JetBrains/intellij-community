// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.LightPlatformTestCase
import org.junit.Test
import java.io.File
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.Path

class GradleLocalPropertiesFileTest : LightPlatformTestCase() {

  private val projectPath by lazy { project.basePath.orEmpty() }
  private val externalProjectPath by lazy { Path(projectPath) }
  private val gradleLocalPropertiesPath by lazy {
    externalProjectPath.resolve(Paths.get(GRADLE_CACHE_DIR_NAME, GRADLE_LOCAL_PROPERTIES_FILE_NAME)).toString()
  }

  @Test
  fun testDefaultGradleLocalProperties() {
    val gradleLocalProperties = GradleLocalProperties.EMPTY
    assertNull(gradleLocalProperties.javaHomeProperty)
  }

  @Test
  fun testNotPresentProjectGradleLocalPropertiesFile() {
    GradleLocalPropertiesFile.getProperties(project, externalProjectPath).run {
      assertEquals(GradleLocalProperties.EMPTY, this)
    }
  }

  @Test
  fun testEmptyProjectGradleLocalPropertiesFile() = testGradleLocalProperties(
    assertion = {
      GradleLocalPropertiesFile.getProperties(project, externalProjectPath).run {
        assertNull(javaHomeProperty)
      }
    }
  )

  @Test
  fun testUnexpectedPropertiesInProjectLocalPropertiesFile() = testGradleLocalProperties(
    configure = {
      setProperty("another.property.1", "value1")
      setProperty("another.property.2", "value2")
    },
    assertion = {
      GradleLocalPropertiesFile.getProperties(project, externalProjectPath).run {
        assertNull(javaHomeProperty)
      }
    }
  )

  @Test
  fun testExpectedPropertiesInProjectGradleLocalPropertiesFile() = testGradleLocalProperties(
    configure = {
      setProperty(GRADLE_LOCAL_JAVA_HOME_PROPERTY, "javaHome")
    },
    assertion = {
      GradleLocalPropertiesFile.getProperties(project, externalProjectPath).run {
        assertEquals("javaHome", javaHomeProperty?.value)
        assertEquals(gradleLocalPropertiesPath, javaHomeProperty?.location)
      }
    }
  )

  @Test
  fun testMultiplePropertiesInProjectGradleLocalPropertiesFile() = testGradleLocalProperties(
    configure = {
      setProperty("another.property.1", "value1")
      setProperty(GRADLE_LOCAL_JAVA_HOME_PROPERTY, "value2")
      setProperty("another.property.2", "value3")
    },
    assertion = {
      GradleLocalPropertiesFile.getProperties(project, externalProjectPath).run {
        assertEquals("value2", javaHomeProperty?.value)
        assertEquals(gradleLocalPropertiesPath, javaHomeProperty?.location)
      }
    }
  )

  private fun testGradleLocalProperties(
    configure: Properties.() -> Unit = {},
    assertion: () -> Unit
  ) {
    val gradleLocalPropertiesFile = File(gradleLocalPropertiesPath)
    FileUtil.createIfNotExists(gradleLocalPropertiesFile)
    gradleLocalPropertiesFile.outputStream().use {
      Properties().run {
        configure()
        store(it, null)
      }
    }
    assertion()
    gradleLocalPropertiesFile.delete()
  }
}