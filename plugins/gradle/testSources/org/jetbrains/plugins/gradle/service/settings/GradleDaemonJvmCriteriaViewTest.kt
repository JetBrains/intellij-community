// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.settings

import com.intellij.ui.dsl.builder.impl.CollapsibleTitledSeparatorImpl
import com.intellij.util.ui.UIUtil
import org.gradle.internal.jvm.inspection.JvmVendor.KnownJvmVendor.ADOPTOPENJDK
import org.gradle.internal.jvm.inspection.JvmVendor.KnownJvmVendor.JETBRAINS
import org.jetbrains.plugins.gradle.service.settings.GradleDaemonJvmCriteriaView.VendorItem
import org.jetbrains.plugins.gradle.service.settings.GradleDaemonJvmCriteriaView.VersionItem
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GradleDaemonJvmCriteriaViewTest: GradleDaemonJvmCriteriaViewTestCase() {

  @Test
  fun `test When creating view Then has the expected defaults`() {
    createGradleDaemonJvmCriteriaView(null, null, IntRange.EMPTY, emptyList(), false).run {
      assertFalse(isModified)

      assertFalse(isValidVersion)
      assertNull(initialCriteria.version)
      assertEquals(0, versionModel.size)

      assertTrue(isValidVendor)
      assertNull(initialCriteria.vendor)

      assertEquals(2, vendorModel.size)
      assertEquals(VendorItem.Any, vendorModel.getElementAt(0))
      assertEquals(VendorItem.SelectCustom, vendorModel.getElementAt(1))
    }
  }

  @Test
  fun `test Given valid version and vendor When creating view Then expected values are displayed`() {
    val versions = 1..10
    val vendors = listOf(ADOPTOPENJDK, JETBRAINS)
    createGradleDaemonJvmCriteriaView("17", "IBM", versions, vendors, false).run {
      assertEquals("17", initialCriteria.version)
      assertVersionDropdownList(versions, versionModel)
      assertTrue(isValidVersion)

      assertEquals("IBM", initialCriteria.vendor)
      assertVendorDropdownList(vendors, vendorModel)
      assertTrue(isValidVendor)
    }
  }

  @Test
  fun `test Given invalid version and vendor When creating view Then expected values are displayed`() {
    createGradleDaemonJvmCriteriaView("Invalid version", " ", IntRange.EMPTY, emptyList(), false).run {
      assertEquals("Invalid version", initialCriteria.version)
      assertFalse(isValidVersion)

      assertEquals(null, initialCriteria.vendor)
      assertFalse(isValidVendor)
    }
  }

  @Test
  fun `test Given enabled advance settings When creating view Then component is displayed`() {
    createGradleDaemonJvmCriteriaView(null, null, IntRange.EMPTY, emptyList(), false).run {
      val advancedSettingsComponent = UIUtil.findComponentOfType(this, CollapsibleTitledSeparatorImpl::class.java)
      assertEquals("Advanced Settings", advancedSettingsComponent?.text)
      assertEquals(false, advancedSettingsComponent?.isVisible)
    }
  }

  @Test
  fun `test Given disabled advance settings When creating view Then component is hidden`() {
    createGradleDaemonJvmCriteriaView(null, null, IntRange.EMPTY, emptyList(), true).run {
      val advancedSettingsComponent = UIUtil.findComponentOfType(this, CollapsibleTitledSeparatorImpl::class.java)
      assertEquals("Advanced Settings", advancedSettingsComponent?.text)
      assertEquals(true, advancedSettingsComponent?.isVisible)
    }
  }

  @Test
  fun `test Given created view When selecting different dropdown items Then selection got updated`() {
    val vendors = listOf(ADOPTOPENJDK)
    createGradleDaemonJvmCriteriaView("1", "vendor", 1..2, vendors, true).run {
      assertFalse(isModified)
      assertEquals("1", initialCriteria.version)
      assertEquals("vendor", initialCriteria.vendor)
      assertEquals(VersionItem.Default(1), selectedVersion)
      assertEquals(VendorItem.Custom("vendor"), selectedVendor)

      selectedVersion = VersionItem.Default(2)
      selectedVendor = VendorItem.Default(ADOPTOPENJDK)
      assertTrue(isModified)
      assertEquals("1", initialCriteria.version)
      assertEquals("vendor", initialCriteria.vendor)
      assertEquals(VersionItem.Default(2), selectedVersion)
      assertEquals(VendorItem.Default(ADOPTOPENJDK), selectedVendor)
    }
  }

  @Test
  fun `test Given created view When apply and reset selection Then initial values got overridden`() {
    val vendors = listOf(ADOPTOPENJDK, JETBRAINS)
    createGradleDaemonJvmCriteriaView("1", "vendor", 1..3, vendors, true).run {
      assertFalse(isModified)
      assertEquals("1", initialCriteria.version)
      assertEquals("vendor", initialCriteria.vendor)
      assertEquals(VersionItem.Default(1), selectedVersion)
      assertEquals(VendorItem.Custom("vendor"), selectedVendor)

      selectedVersion = VersionItem.Default(2)
      selectedVendor = VendorItem.Default(ADOPTOPENJDK)
      assertTrue(isModified)
      assertEquals("1", initialCriteria.version)
      assertEquals("vendor", initialCriteria.vendor)
      assertEquals(VersionItem.Default(2), selectedVersion)
      assertEquals(VendorItem.Default(ADOPTOPENJDK), selectedVendor)

      applySelection()
      assertFalse(isModified)
      assertEquals("2", initialCriteria.version)
      assertEquals("ADOPTOPENJDK", initialCriteria.vendor)
      assertEquals(VersionItem.Default(2), selectedVersion)
      assertEquals(VendorItem.Default(ADOPTOPENJDK), selectedVendor)

      selectedVersion = VersionItem.Default(3)
      selectedVendor = VendorItem.Default(JETBRAINS)
      assertTrue(isModified)
      assertEquals("2", initialCriteria.version)
      assertEquals("ADOPTOPENJDK", initialCriteria.vendor)
      assertEquals(VersionItem.Default(3), selectedVersion)
      assertEquals(VendorItem.Default(JETBRAINS), selectedVendor)

      resetSelection()
      assertFalse(isModified)
      assertEquals("2", initialCriteria.version)
      assertEquals("ADOPTOPENJDK", initialCriteria.vendor)
      assertEquals(VersionItem.Default(2), selectedVersion)
      assertEquals(VendorItem.Default(ADOPTOPENJDK), selectedVendor)
    }
  }
}