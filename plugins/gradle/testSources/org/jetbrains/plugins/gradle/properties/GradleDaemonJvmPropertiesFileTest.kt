// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.LightPlatformTestCase
import java.io.File
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.Path

class GradleDaemonJvmPropertiesFileTest : LightPlatformTestCase() {

  private val projectPath by lazy { project.basePath.orEmpty() }
  private val externalProjectPath by lazy { Path(projectPath) }
  private val gradleDaemonJvmPropertiesPath by lazy {
    externalProjectPath.resolve(Paths.get(GRADLE_FOLDER, "gradle-daemon-jvm.properties")).toString()
  }

  fun testNotPresentProjectGradleDaemonJvmPropertiesFile() {
    assertNull(GradleDaemonJvmPropertiesFile.getProperties(externalProjectPath))
  }

  fun testEmptyProjectGradleDaemonJvmPropertiesFile() = testGradleDaemonJvmProperties(
    assertion = {
      GradleDaemonJvmPropertiesFile.getProperties(externalProjectPath)!!.run {
        assertNull(version)
        assertNull(vendor)
      }
    }
  )

  fun testUnexpectedPropertiesInProjectGradleDaemonJvmPropertiesFile() = testGradleDaemonJvmProperties(
    configure = {
      setProperty("another.property.1", "value1")
      setProperty("another.property.2", "value2")
    },
    assertion = {
      GradleDaemonJvmPropertiesFile.getProperties(externalProjectPath)!!.run {
        assertNull(version)
        assertNull(vendor)
      }
    }
  )

  fun testPropertiesInProjectGradleDaemonJvmPropertiesFile() = testGradleDaemonJvmProperties(
    configure = {
      setProperty("another.property.1", "value1")
      setProperty("toolchainVersion", "value2")
      setProperty("another.property.2", "value3")
      setProperty("toolchainVendor", "value4")
    },
    assertion = {
      GradleDaemonJvmPropertiesFile.getProperties(externalProjectPath)!!.run {
        assertEquals("value2", version?.value)
        assertEquals(gradleDaemonJvmPropertiesPath, version?.location)
        assertEquals("value4", vendor?.value)
        assertEquals(gradleDaemonJvmPropertiesPath, vendor?.location)
      }
    }
  )

  private fun testGradleDaemonJvmProperties(
    configure: Properties.() -> Unit = {},
    assertion: () -> Unit
  ) {
    val gradleDaemonJvmPropertiesFile = File(gradleDaemonJvmPropertiesPath)
    FileUtil.createIfNotExists(gradleDaemonJvmPropertiesFile)
    gradleDaemonJvmPropertiesFile.outputStream().use {
      Properties().run {
        configure()
        store(it, null)
      }
    }
    assertion()
    gradleDaemonJvmPropertiesFile.delete()
  }
}