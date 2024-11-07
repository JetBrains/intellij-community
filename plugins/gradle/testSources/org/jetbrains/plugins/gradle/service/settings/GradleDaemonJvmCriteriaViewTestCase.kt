// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.settings

import com.intellij.openapi.Disposable
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
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
    vendorDropdownList: List<String>,
    displayAdvancedSettings: Boolean,
  ) = GradleDaemonJvmCriteriaView(
    version,
    vendor,
    versionsDropdownList,
    vendorDropdownList,
    displayAdvancedSettings,
    testRootDisposable
  )

  fun assertVersionDropdownList(versions: IntRange, model: ComboBoxModel<String>) {
    versions.reversed().forEachIndexed { index, expectedVersion ->
      assertEquals(expectedVersion.toString(), model.getElementAt(index))
    }
  }

  fun assertVendorDropdownList(vendors: List<String>, model: ComboBoxModel<String>) {
    (listOf("<ANY_VENDOR>", "<CUSTOM_VENDOR>") + vendors).forEachIndexed { index, expectedVendor ->
      assertEquals(expectedVendor, model.getElementAt(index))
    }
  }
}