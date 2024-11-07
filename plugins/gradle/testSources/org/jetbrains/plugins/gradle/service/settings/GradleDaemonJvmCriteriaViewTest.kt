// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.settings

import com.intellij.ui.dsl.builder.impl.CollapsibleTitledSeparatorImpl
import com.intellij.util.ui.UIUtil
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GradleDaemonJvmCriteriaViewTest: GradleDaemonJvmCriteriaViewTestCase() {

  @Test
  fun `test When creating view Then has the expected defaults`() {
    createGradleDaemonJvmCriteriaView(null, null, IntRange.EMPTY, emptyList(), false).run {
      assertFalse(isModified)

      assertFalse(isValidVersion)
      assertEquals("UNDEFINED", selectedCriteria.version)
      assertEquals(0, versionComboBox.itemCount)

      assertTrue(isValidVendor)
      assertNull(selectedCriteria.vendor)

      assertEquals(2, vendorComboBox.itemCount)
      assertEquals("<ANY_VENDOR>", vendorComboBox.model.getElementAt(0))
      assertEquals("<CUSTOM_VENDOR>", vendorComboBox.model.getElementAt(1))
    }
  }

  @Test
  fun `test Given valid version and vendor When creating view Then expected values are displayed`() {
    val versions = 1..10
    val vendors = listOf("a", "d", "b", "c")
    createGradleDaemonJvmCriteriaView("17", "IBM", versions, vendors, false).run {
      assertEquals("17", selectedCriteria.version)
      assertVersionDropdownList(versions, versionComboBox.model)
      assertTrue(isValidVersion)

      assertEquals("IBM", selectedCriteria.vendor)
      assertVendorDropdownList(vendors, vendorComboBox.model)
      assertTrue(isValidVendor)
    }
  }

  @Test
  fun `test Given invalid version and vendor When creating view Then expected values are displayed`() {
    createGradleDaemonJvmCriteriaView("Invalid version", " ", IntRange.EMPTY, emptyList(), false).run {
      assertEquals("Invalid version", selectedCriteria.version)
      assertFalse(isValidVersion)

      assertEquals(null, selectedCriteria.vendor)
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
    createGradleDaemonJvmCriteriaView("1", "vendor", 1..2, listOf("new vendor"), true).run {
      versionComboBox.selectedItem = "2"
      vendorComboBox.selectedItem = "new vendor"
      assertTrue(isModified)
      assertEquals("2", selectedCriteria.version)
      assertEquals("new vendor", selectedCriteria.vendor)
    }
  }

  @Test
  fun `test Given created view When apply and reset selection Then initial values got overridden`() {
    createGradleDaemonJvmCriteriaView("1", "vendor", 1..3, listOf("new vendor", "other vendor"), true).run {
      versionComboBox.selectedItem = "2"
      vendorComboBox.selectedItem = "new vendor"
      applySelection().run {
        assertFalse(isModified)
      }

      versionComboBox.selectedItem = "3"
      vendorComboBox.selectedItem = "other vendor"
      resetSelection().run {
        assertFalse(isModified)
        assertEquals("2", selectedCriteria.version)
        assertEquals("new vendor", selectedCriteria.vendor)
      }
    }
  }
}