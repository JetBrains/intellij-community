// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.settings

import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.ui.JBColor
import com.intellij.ui.dsl.builder.impl.CollapsibleTitledSeparatorImpl
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.gradle.properties.models.Property
import javax.swing.ComboBoxModel

class GradleDaemonJvmCriteriaViewTest : LightPlatformTestCase() {

  fun `test When creating view Then has the expected defaults`() {
    createGradleDaemonJvmCriteriaView(null, null, IntRange.EMPTY, emptyList(), false).run {
      assertFalse(isModified)

      assertFalse(isValidVersion)
      assertEquals("UNDEFINED", selectedVersion)
      assertEquals(0, versionComboBox.itemCount)

      assertTrue(isValidVendor)
      assertEquals("<ANY_VENDOR>", selectedVendor)
      assertEquals(1, vendorComboBox.itemCount)
      assertEquals("<ANY_VENDOR>", vendorComboBox.model.getElementAt(0))
    }
  }

  fun `test Given valid version and vendor When creating view Then expected values are displayed`() {
    val versions = 1..10
    val vendors = listOf("a", "d", "b", "c")
    createGradleDaemonJvmCriteriaView("17", "IBM", versions, vendors, false).run {
      assertEquals("17", selectedVersion)
      assertVersionDropdownList(versions, versionComboBox.model)
      assertEquals(JBColor.black, versionComboBox.editor.editorComponent.foreground)
      assertTrue(isValidVersion)

      assertEquals("IBM", selectedVendor)
      assertVendorDropdownList(vendors, vendorComboBox.model)
      assertEquals(JBColor.black, vendorComboBox.editor.editorComponent.foreground)
      assertTrue(isValidVendor)
    }
  }

  fun `test Given invalid version and vendor When creating view Then expected values are displayed`() {
    createGradleDaemonJvmCriteriaView("Invalid version", " ", IntRange.EMPTY, emptyList(), false).run {
      assertEquals("Invalid version", selectedVersion)
      assertEquals(JBColor.red, versionComboBox.editor.editorComponent.foreground)
      assertFalse(isValidVersion)

      assertEquals(" ", selectedVendor)
      assertEquals(JBColor.red, vendorComboBox.editor.editorComponent.foreground)
      assertFalse(isValidVendor)
    }
  }

  fun `test Given enabled advance settings When creating view Then component is displayed`() {
    createGradleDaemonJvmCriteriaView(null, null, IntRange.EMPTY, emptyList(), false).run {
      val advancedSettingsComponent = UIUtil.findComponentOfType(this, CollapsibleTitledSeparatorImpl::class.java)
      assertEquals("Advanced Settings", advancedSettingsComponent?.text)
      assertEquals(false, advancedSettingsComponent?.isVisible)
    }
  }

  fun `test Given disabled advance settings When creating view Then component is hidden`() {
    createGradleDaemonJvmCriteriaView(null, null, IntRange.EMPTY, emptyList(), true).run {
      val advancedSettingsComponent = UIUtil.findComponentOfType(this, CollapsibleTitledSeparatorImpl::class.java)
      assertEquals("Advanced Settings", advancedSettingsComponent?.text)
      assertEquals(true, advancedSettingsComponent?.isVisible)
    }
  }

  fun `test Given created view When selecting different dropdown items Then selection got updated`() {
    createGradleDaemonJvmCriteriaView("version", "vendor", IntRange.EMPTY, emptyList(), true).run {
      versionComboBox.selectedItem = "new version"
      vendorComboBox.selectedItem = "new vendor"
      assertTrue(isModified)
      assertEquals("new version", selectedVersion)
      assertEquals("new vendor", selectedVendor)
    }
  }

  fun `test Given created view When apply and reset selection Then initial values got overridden`() {
    createGradleDaemonJvmCriteriaView("version", "vendor", IntRange.EMPTY, emptyList(), true).run {
      versionComboBox.selectedItem = "new version"
      vendorComboBox.selectedItem = "new vendor"
      applySelection().run {
        assertFalse(isModified)
      }

      versionComboBox.selectedItem = "other version"
      vendorComboBox.selectedItem = "other vendor"
      resetSelection().run {
        assertFalse(isModified)
        assertEquals("new version", selectedVersion)
        assertEquals("new vendor", selectedVendor)
      }
    }
  }

  private fun createGradleDaemonJvmCriteriaView(
    version: String?,
    vendor: String?,
    versionsDropdownList: IntRange,
    vendorDropdownList: List<String>,
    displayAdvancedSettings: Boolean,
  ) = GradleDaemonJvmCriteriaView(
    version?.let { Property(it, "test/location") },
    vendor?.let { Property(it, "test/location") },
    versionsDropdownList,
    vendorDropdownList,
    displayAdvancedSettings
  )

  private fun assertVersionDropdownList(versions: IntRange, model: ComboBoxModel<Int>) {
    versions.reversed().forEachIndexed { index, expectedVersion ->
      assertEquals(expectedVersion, model.getElementAt(index))
    }
  }

  private fun assertVendorDropdownList(vendors: List<String>, model: ComboBoxModel<String>) {
    (listOf("<ANY_VENDOR>") + vendors).forEachIndexed { index, expectedVendor ->
      assertEquals(expectedVendor, model.getElementAt(index))
    }
  }
}