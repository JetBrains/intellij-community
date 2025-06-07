// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class GradleLocalPropertiesFileTest : GradleLocalPropertiesFileTestCase() {

  @Test
  fun testEmptyProjectGradleLocalPropertiesFile() {
    createGradleLocalPropertiesFile {}
    asserGradleLocalPropertiesFile {
      Assertions.assertNull(javaHomeProperty)
    }
  }

  @Test
  fun testUnexpectedPropertiesInProjectLocalPropertiesFile() {
    createGradleLocalPropertiesFile {
      setProperty("another.property.1", "value1")
      setProperty("another.property.2", "value2")
    }
    asserGradleLocalPropertiesFile {
      Assertions.assertNull(javaHomeProperty)
    }
  }

  @Test
  fun testExpectedPropertiesInProjectGradleLocalPropertiesFile() {
    createGradleLocalPropertiesFile {
      setProperty(GRADLE_LOCAL_JAVA_HOME_PROPERTY, "javaHome")
    }
    asserGradleLocalPropertiesFile {
      Assertions.assertEquals("javaHome", javaHomeProperty?.value)
      Assertions.assertEquals(projectPropertiesPath, javaHomeProperty?.location)
    }
  }

  @Test
  fun testMultiplePropertiesInProjectGradleLocalPropertiesFile() {
    createGradleLocalPropertiesFile {
      setProperty("another.property.1", "value1")
      setProperty(GRADLE_LOCAL_JAVA_HOME_PROPERTY, "value2")
      setProperty("another.property.2", "value3")
    }
    asserGradleLocalPropertiesFile {
      Assertions.assertEquals("value2", javaHomeProperty?.value)
      Assertions.assertEquals(projectPropertiesPath, javaHomeProperty?.location)
    }
  }
}