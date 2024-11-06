// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.settings

import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.VfsTestUtil
import com.intellij.ui.dsl.builder.impl.CollapsibleTitledSeparatorImpl
import com.intellij.util.ui.UIUtil
import org.gradle.util.GradleVersion
import org.jetbrains.jps.model.java.LanguageLevel
import java.nio.file.Path

class GradleDaemonJvmCriteriaViewFactoryTest : LightPlatformTestCase() {

  fun `test Given gradle version unsupporting toolchain vendor When create view Then advance settings is hidden`() {
    createDaemonJvmCriteriaView(GradleVersion.version("8.0")).run {
      assertAdvancedSettingsIsVisible(false)
    }
  }

  fun `test Given gradle version supporting toolchain vendor When create view Then advance settings is visible`() {
    createDaemonJvmCriteriaView(GradleVersion.version("8.10")).run {
      assertAdvancedSettingsIsVisible(true)
    }
  }

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

  fun `test Given empty gradle jvm properties When create view Then expected values are displayed`() {
    createDaemonJvmPropertiesFile("")
    createDaemonJvmCriteriaView(GradleVersion.version("8.9")).run {
      assertEquals("UNDEFINED", selectedCriteria.version)
      assertEquals(null, selectedCriteria.vendor)
    }
  }

  fun `test Given created view Then dropdown items are the expected ones`() {
    createDaemonJvmCriteriaView(GradleVersion.version("8.9")).run {
      assertVersionDropdownItems()
      assertVendorDropdownItems()
    }
  }

  private fun createDaemonJvmCriteriaView(gradleVersion: GradleVersion) =
    GradleDaemonJvmCriteriaViewFactory.createView(Path.of(project.basePath!!), gradleVersion, testRootDisposable)

  private fun createDaemonJvmPropertiesFile(content: String) {
    VfsTestUtil.createFile(project.baseDir, "gradle/gradle-daemon-jvm.properties", content)
  }

  private fun GradleDaemonJvmCriteriaView.assertAdvancedSettingsIsVisible(isVisible: Boolean) {
    val advancedSettingsComponent = UIUtil.findComponentOfType(this, CollapsibleTitledSeparatorImpl::class.java)
    assertEquals("Advanced Settings", advancedSettingsComponent?.text)
    assertEquals(isVisible, advancedSettingsComponent?.isVisible)
  }

  private fun GradleDaemonJvmCriteriaView.assertVersionDropdownItems() {
    val expectedVersionList = LanguageLevel.HIGHEST.toJavaVersion().feature.downTo(8)
    expectedVersionList.forEachIndexed { index, expectedVersion ->
      assertEquals(expectedVersion.toString(), versionComboBox.model.getElementAt(index))
    }
  }

  private fun GradleDaemonJvmCriteriaView.assertVendorDropdownItems() {
    val expectedVendorList = listOf("<ANY_VENDOR>", "<CUSTOM_VENDOR>", "ADOPTIUM", "ADOPTOPENJDK", "AMAZON", "APPLE", "AZUL", "BELLSOFT", "GRAAL_VM",
                                    "HEWLETT_PACKARD", "IBM", "JETBRAINS", "MICROSOFT", "ORACLE", "SAP", "TENCENT")
    expectedVendorList.forEachIndexed { index, expectedVendor ->
      assertEquals(expectedVendor, vendorComboBox.model.getElementAt(index))
    }
  }
}