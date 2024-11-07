// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.settings

import org.gradle.util.GradleVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GradleDaemonJvmCriteriaViewFactoryTest : GradleDaemonJvmCriteriaViewFactoryTestCase() {

  @Test
  fun `test Given gradle version unsupporting toolchain vendor When create view Then advance settings is hidden`() {
    createDaemonJvmCriteriaView(GradleVersion.version("8.0")).run {
      assertAdvancedSettingsIsVisible(false)
    }
  }

  @Test
  fun `test Given gradle version supporting toolchain vendor When create view Then advance settings is visible`() {
    createDaemonJvmCriteriaView(GradleVersion.version("8.10")).run {
      assertAdvancedSettingsIsVisible(true)
    }
  }

  @Test
  fun `test Given gradle jvm properties with expected values When create view Then expected values are displayed`() {
    createDaemonJvmPropertiesFile("""
       toolchainVendor=AZUL
       toolchainVersion=19
    """.trimIndent())

    createDaemonJvmCriteriaView(GradleVersion.version("8.9")).run {
      assertEquals("19", selectedCriteria.version)
      assertEquals("AZUL", selectedCriteria.vendor)
    }
  }

  @Test
  fun `test Given gradle jvm properties with unexpected values When create view Then expected values are displayed`() {
    createDaemonJvmPropertiesFile("""
       toolchainVendor=any other vendor
       toolchainVersion=string version
    """.trimIndent())

    createDaemonJvmCriteriaView(GradleVersion.version("8.9")).run {
      assertEquals("string version", selectedCriteria.version)
      assertEquals("any other vendor", selectedCriteria.vendor)
    }
  }

  @Test
  fun `test Given empty gradle jvm properties When create view Then expected values are displayed`() {
    createDaemonJvmPropertiesFile("")
    createDaemonJvmCriteriaView(GradleVersion.version("8.9")).run {
      assertEquals("UNDEFINED", selectedCriteria.version)
      assertEquals(null, selectedCriteria.vendor)
    }
  }

  @Test
  fun `test Given created view Then dropdown items are the expected ones`() {
    createDaemonJvmCriteriaView(GradleVersion.version("8.9")).run {
      assertVersionDropdownItems()
      assertVendorDropdownItems()
    }
  }
}