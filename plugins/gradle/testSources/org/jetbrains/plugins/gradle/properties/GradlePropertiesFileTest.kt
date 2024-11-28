// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties

import org.gradle.api.logging.LogLevel
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class GradlePropertiesFileTest : GradlePropertiesFileTestCase() {

  @Test
  fun testEmptyProjectGradlePropertiesFile() {
    createGradlePropertiesFile {}
    assertGradlePropertiesFile {
      Assertions.assertNull(javaHomeProperty)
      Assertions.assertNull(logLevel)
      Assertions.assertNull(parallel)
      Assertions.assertNull(isolatedProjects)
      Assertions.assertNull(jvmOptions)
    }
  }

  @Test
  fun testUnexpectedPropertiesInProjectGradlePropertiesFile() {
    createGradlePropertiesFile {
      setProperty("another.property.1", "value1")
      setProperty("another.property.2", "value2")
    }
    assertGradlePropertiesFile {
      Assertions.assertNull(javaHomeProperty)
      Assertions.assertNull(logLevel)
      Assertions.assertNull(parallel)
      Assertions.assertNull(isolatedProjects)
      Assertions.assertNull(jvmOptions)
    }
  }

  @Test
  fun testExpectedPropertiesInProjectGradlePropertiesFile() {
    createGradlePropertiesFile {
      setProperty(GRADLE_JAVA_HOME_PROPERTY, "javaHome")
      setProperty(GRADLE_LOGGING_LEVEL_PROPERTY, "info")
      setProperty(GRADLE_PARALLEL_PROPERTY, "true")
      setProperty(GRADLE_ISOLATED_PROJECTS_PROPERTY, "true")
      setProperty(GRADLE_JVM_OPTIONS_PROPERTY, "-Xmx20G")
    }
    assertGradlePropertiesFile {
      Assertions.assertEquals("javaHome", javaHomeProperty?.value)
      Assertions.assertEquals(projectPropertiesPath, javaHomeProperty?.location)
      Assertions.assertEquals("info", logLevel?.value)
      Assertions.assertEquals(LogLevel.INFO, getGradleLogLevel())
      Assertions.assertEquals(projectPropertiesPath, logLevel?.location)
      Assertions.assertEquals(true, parallel?.value)
      Assertions.assertEquals(projectPropertiesPath, parallel?.location)
      Assertions.assertEquals(true, isolatedProjects?.value)
      Assertions.assertEquals(projectPropertiesPath, isolatedProjects?.location)
      Assertions.assertEquals("-Xmx20G", jvmOptions?.value)
      Assertions.assertEquals(projectPropertiesPath, jvmOptions?.location)
    }
  }

  @Test
  fun testMultiplePropertiesInProjectGradlePropertiesFile() {
    createGradlePropertiesFile {
      setProperty("another.property.1", "value1")
      setProperty(GRADLE_JAVA_HOME_PROPERTY, "value2")
      setProperty(GRADLE_LOGGING_LEVEL_PROPERTY, "debug")
      setProperty(GRADLE_PARALLEL_PROPERTY, "true")
      setProperty(GRADLE_ISOLATED_PROJECTS_PROPERTY, "true")
      setProperty(GRADLE_JVM_OPTIONS_PROPERTY, "-Xmx20G")
      setProperty("another.property.2", "value4")
    }
    assertGradlePropertiesFile {
      Assertions.assertEquals("value2", javaHomeProperty?.value)
      Assertions.assertEquals(projectPropertiesPath, javaHomeProperty?.location)
      Assertions.assertEquals("debug", logLevel?.value)
      Assertions.assertEquals(LogLevel.DEBUG, getGradleLogLevel())
      Assertions.assertEquals(projectPropertiesPath, logLevel?.location)
      Assertions.assertEquals(true, parallel?.value)
      Assertions.assertEquals(projectPropertiesPath, parallel?.location)
      Assertions.assertEquals(true, isolatedProjects?.value)
      Assertions.assertEquals(projectPropertiesPath, isolatedProjects?.location)
      Assertions.assertEquals("-Xmx20G", jvmOptions?.value)
      Assertions.assertEquals(projectPropertiesPath, jvmOptions?.location)
    }
  }

  @Test
  fun testInvalidLogLevelInProjectGradlePropertiesFile() {
    createGradlePropertiesFile {
      setProperty(GRADLE_LOGGING_LEVEL_PROPERTY, "invalid")
    }
    assertGradlePropertiesFile {
      Assertions.assertEquals("invalid", logLevel?.value)
      Assertions.assertEquals(projectPropertiesPath, logLevel?.location)

      Assertions.assertNull(getGradleLogLevel())
    }
  }
}