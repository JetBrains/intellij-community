// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.settings

import com.intellij.openapi.Disposable
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.ui.dsl.builder.impl.CollapsibleTitledSeparatorImpl
import com.intellij.util.ui.UIUtil
import org.gradle.util.GradleVersion
import org.jetbrains.jps.model.java.LanguageLevel
import org.jetbrains.plugins.gradle.service.settings.GradleDaemonJvmCriteriaView.VendorItem
import org.jetbrains.plugins.gradle.service.settings.GradleDaemonJvmCriteriaView.VersionItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
abstract class GradleDaemonJvmCriteriaViewFactoryTestCase {

  @TempDir
  private lateinit var projectRoot: Path

  @TestDisposable
  private lateinit var testRootDisposable: Disposable

  fun createDaemonJvmCriteriaView(gradleVersion: GradleVersion) =
    GradleDaemonJvmCriteriaViewFactory.createView(projectRoot, gradleVersion, testRootDisposable)

  fun createDaemonJvmPropertiesFile(content: String) {
    Files.createDirectories(projectRoot.resolve("gradle"))
    Files.writeString(projectRoot.resolve("gradle/gradle-daemon-jvm.properties"), content)
  }

  fun GradleDaemonJvmCriteriaView.assertAdvancedSettingsIsVisible(isVisible: Boolean) {
    val advancedSettingsComponent = UIUtil.findComponentOfType(this, CollapsibleTitledSeparatorImpl::class.java)
    assertEquals("Advanced Settings", advancedSettingsComponent?.text)
    assertEquals(isVisible, advancedSettingsComponent?.isVisible)
  }

  fun GradleDaemonJvmCriteriaView.assertVersionDropdownItems() {
    val expectedVersionList = LanguageLevel.HIGHEST.toJavaVersion().feature.downTo(8)
    expectedVersionList.forEachIndexed { index, expectedVersion ->
      val actualVersion = when (val versionItem = versionModel.getElementAt(index)) {
        is VersionItem.Default -> versionItem.version.toString()
        is VersionItem.Custom -> throw AssertionError("Unexpected custom version item: " + versionItem.value)
      }
      assertEquals(expectedVersion.toString(), actualVersion)
    }
  }

  fun GradleDaemonJvmCriteriaView.assertVendorDropdownItems() {
    val expectedVendorList = listOf("<ANY_VENDOR>", "<CUSTOM_VENDOR>", "ADOPTIUM", "ADOPTOPENJDK", "AMAZON", "APPLE", "AZUL", "BELLSOFT", "GRAAL_VM",
                                    "HEWLETT_PACKARD", "IBM", "JETBRAINS", "MICROSOFT", "ORACLE", "SAP", "TENCENT")
    expectedVendorList.forEachIndexed { index, expectedVendor ->
      val actualVendor = when (val vendorItem = vendorModel.getElementAt(index)) {
        VendorItem.Any -> "<ANY_VENDOR>"
        VendorItem.SelectCustom -> "<CUSTOM_VENDOR>"
        is VendorItem.Default -> vendorItem.vendor.name
        is VendorItem.Custom -> throw AssertionError("Unexpected custom vendor item: " + vendorItem.value)
      }
      assertEquals(expectedVendor, actualVendor)
    }
  }
}