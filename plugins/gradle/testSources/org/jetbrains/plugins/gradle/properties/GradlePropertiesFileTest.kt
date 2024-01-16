// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class GradlePropertiesFileTest : GradlePropertiesFileTestCase() {

  @Test
  fun testDefaultGradleProperties() {
    GradleProperties.EMPTY.run {
      Assertions.assertNull(javaHomeProperty)
      Assertions.assertNull(gradleLoggingLevel)
    }
  }

  @Test
  fun testNotPresentProjectGradlePropertiesFile() {
    assertGradlePropertiesFile {
      Assertions.assertEquals(GradleProperties.EMPTY, this)
    }
  }

  @Test
  fun testEmptyProjectGradlePropertiesFile() {
    assertGradlePropertiesFile {
      Assertions.assertNull(javaHomeProperty)
      Assertions.assertNull(gradleLoggingLevel)
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
      Assertions.assertNull(gradleLoggingLevel)
    }
  }

  @Test
  fun testExpectedPropertiesInProjectGradlePropertiesFile() {
    createGradlePropertiesFile {
      setProperty(GRADLE_JAVA_HOME_PROPERTY, "javaHome")
      setProperty(GRADLE_LOGGING_LEVEL_PROPERTY, "info")
    }
    assertGradlePropertiesFile {
      Assertions.assertEquals("javaHome", javaHomeProperty?.value)
      Assertions.assertEquals(gradlePropertiesPath, javaHomeProperty?.location)
      Assertions.assertEquals("info", gradleLoggingLevel?.value)
      Assertions.assertEquals(gradlePropertiesPath, gradleLoggingLevel?.location)
    }
  }

  @Test
  fun testMultiplePropertiesInProjectGradlePropertiesFile() {
    createGradlePropertiesFile {
      setProperty("another.property.1", "value1")
      setProperty(GRADLE_JAVA_HOME_PROPERTY, "value2")
      setProperty(GRADLE_LOGGING_LEVEL_PROPERTY, "value3")
      setProperty("another.property.2", "value4")
    }
    assertGradlePropertiesFile {
      Assertions.assertEquals("value2", javaHomeProperty?.value)
      Assertions.assertEquals(gradlePropertiesPath, javaHomeProperty?.location)
      Assertions.assertEquals("value3", gradleLoggingLevel?.value)
      Assertions.assertEquals(gradlePropertiesPath, gradleLoggingLevel?.location)
    }
  }
}