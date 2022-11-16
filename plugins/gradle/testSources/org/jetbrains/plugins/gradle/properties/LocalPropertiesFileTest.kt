// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.LightPlatformTestCase
import org.junit.Test
import java.io.File
import java.util.*
import kotlin.io.path.Path

class LocalPropertiesFileTest : LightPlatformTestCase() {

  private val projectPath by lazy { project.basePath.orEmpty() }
  private val externalProjectPath by lazy { Path(projectPath) }
  private val localPropertiesPath by lazy { externalProjectPath.resolve(LOCAL_PROPERTIES_FILE_NAME).toString() }

  override fun tearDown() {
    super.tearDown()
    closeAndDeleteProject()
  }

  @Test
  fun testDefaultLocalProperties() {
    val localProperties = LocalProperties.EMPTY
    assertNull(localProperties.javaHomeProperty)
  }

  @Test
  fun testNotPresentProjectLocalPropertiesFile() {
    LocalPropertiesFile.getProperties(project, externalProjectPath).run {
      assertEquals(LocalProperties.EMPTY, this)
    }
  }

  @Test
  fun testEmptyProjectLocalPropertiesFile() = testLocalProperties(
    assertion = {
      LocalPropertiesFile.getProperties(project, externalProjectPath).run {
        assertNull(javaHomeProperty)
      }
    }
  )

  @Test
  fun testUnexpectedPropertiesInProjectLocalPropertiesFile() = testLocalProperties(
    configure = {
      setProperty("another.property.1", "value1")
      setProperty("another.property.2", "value2")
    },
    assertion = {
      LocalPropertiesFile.getProperties(project, externalProjectPath).run {
        assertNull(javaHomeProperty)
      }
    }
  )

  @Test
  fun testExpectedPropertiesInProjectLocalPropertiesFile() = testLocalProperties(
    configure = {
      setProperty(LOCAL_JAVA_HOME_PROPERTY, "javaHome")
    },
    assertion = {
      LocalPropertiesFile.getProperties(project, externalProjectPath).run {
        assertEquals("javaHome", javaHomeProperty?.value)
        assertEquals(localPropertiesPath, javaHomeProperty?.location)
      }
    }
  )

  @Test
  fun testMultiplePropertiesInProjectLocalPropertiesFile() = testLocalProperties(
    configure = {
      setProperty("another.property.1", "value1")
      setProperty(LOCAL_JAVA_HOME_PROPERTY, "value2")
      setProperty("another.property.2", "value3")
    },
    assertion = {
      LocalPropertiesFile.getProperties(project, externalProjectPath).run {
        assertEquals("value2", javaHomeProperty?.value)
        assertEquals(localPropertiesPath, javaHomeProperty?.location)
      }
    }
  )

  private fun testLocalProperties(
    configure: Properties.() -> Unit = {},
    assertion: () -> Unit
  ) {
    val localPropertiesFile = File(localPropertiesPath)
    FileUtil.createIfNotExists(localPropertiesFile)
    localPropertiesFile.outputStream().use {
      Properties().run {
        configure()
        store(it, null)
      }
    }
    assertion()
  }
}