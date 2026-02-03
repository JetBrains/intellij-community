// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@TestApplication
class GradleDaemonJvmPropertiesFileTest : GradleDaemonJvmPropertiesFileTestCase() {

  @Test
  fun testNotPresentProjectGradleDaemonJvmPropertiesFile() {
    assertGradleDaemonJvmPropertiesFile {
      Assertions.assertNull(version)
      Assertions.assertNull(vendor)
    }
  }

  @Test
  fun testEmptyProjectGradleDaemonJvmPropertiesFile() {
    createGradleDaemonJvmPropertiesFile {}
    assertGradleDaemonJvmPropertiesFile {
      Assertions.assertNull(version)
      Assertions.assertNull(vendor)
    }
  }

  @Test
  fun testUnexpectedPropertiesInProjectGradleDaemonJvmPropertiesFile() {
    createGradleDaemonJvmPropertiesFile {
      setProperty("another.property.1", "value1")
      setProperty("another.property.2", "value2")
    }
    assertGradleDaemonJvmPropertiesFile {
      Assertions.assertNull(version)
      Assertions.assertNull(vendor)
    }
  }

  @Test
  fun testPropertiesInProjectGradleDaemonJvmPropertiesFile() {
    createGradleDaemonJvmPropertiesFile {
      setProperty("another.property.1", "value1")
      setProperty("toolchainVersion", "value2")
      setProperty("another.property.2", "value3")
      setProperty("toolchainVendor", "value4")
    }
    assertGradleDaemonJvmPropertiesFile {
      Assertions.assertEquals("value2", version?.value)
      Assertions.assertEquals(projectPropertiesPath, version?.location)
      Assertions.assertEquals("value4", vendor?.value)
      Assertions.assertEquals(projectPropertiesPath, vendor?.location)
    }
  }
}