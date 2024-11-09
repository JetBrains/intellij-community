// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.settings

import com.intellij.openapi.Disposable
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.gradle.internal.jvm.inspection.JvmVendor
import org.jetbrains.plugins.gradle.service.settings.GradleDaemonJvmCriteriaView.VendorItem
import org.jetbrains.plugins.gradle.service.settings.GradleDaemonJvmCriteriaView.VersionItem
import org.junit.jupiter.api.Assertions.assertEquals
import javax.swing.ComboBoxModel

@TestApplication
abstract class GradleDaemonJvmCriteriaViewTestCase {

  @TestDisposable
  private lateinit var testRootDisposable: Disposable

  fun createGradleDaemonJvmCriteriaView(
    version: String?,
    vendor: String?,
    versionsDropdownList: IntRange,
    vendorDropdownList: List<JvmVendor.KnownJvmVendor>,
    displayAdvancedSettings: Boolean,
  ) = GradleDaemonJvmCriteriaView(
    version,
    vendor,
    versionsDropdownList,
    vendorDropdownList,
    displayAdvancedSettings,
    testRootDisposable
  )

  fun assertVersionDropdownList(versions: IntRange, model: ComboBoxModel<VersionItem>) {
    versions.reversed().forEachIndexed { index, expectedVersion ->
      val actualVersion = when (val versionItem = model.getElementAt(index)) {
        is VersionItem.Default -> versionItem.version.toString()
        is VersionItem.Custom -> throw AssertionError("Unexpected custom version item: " + versionItem.value)
      }
      assertEquals(expectedVersion.toString(), actualVersion)
    }
  }

  fun assertVendorDropdownList(vendors: List<JvmVendor.KnownJvmVendor>, model: ComboBoxModel<VendorItem>) {
    (listOf("<ANY_VENDOR>", "<CUSTOM_VENDOR>") + vendors.map { it.name }).forEachIndexed { index, expectedVendor ->
      val actualVendor = when (val vendorItem = model.getElementAt(index)) {
        VendorItem.Any -> "<ANY_VENDOR>"
        VendorItem.SelectCustom -> "<CUSTOM_VENDOR>"
        is VendorItem.Default -> vendorItem.vendor.name
        is VendorItem.Custom -> throw AssertionError("Unexpected custom vendor item: " + vendorItem.value)
      }
      assertEquals(expectedVendor, actualVendor)
    }
  }
}