// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.dsl.builder.COLUMNS_SHORT
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmCriteria
import org.jetbrains.plugins.gradle.util.GradleBundle
import javax.swing.JPanel

private const val UNDEFINED_VERSION_PLACEHOLDER = "UNDEFINED"
private const val ANY_VENDOR_PLACEHOLDER = "<ANY_VENDOR>"
private const val CUSTOM_VENDOR_PLACEHOLDER = "<CUSTOM_VENDOR>"

class GradleDaemonJvmCriteriaView(
  version: String?,
  vendor: String?,
  private val versionsDropdownList: IntRange,
  private val vendorDropdownList: List<String>,
  private val displayAdvancedSettings: Boolean,
  disposable: Disposable,
): JPanel(VerticalLayout(0)) {

  val selectedCriteria: GradleDaemonJvmCriteria
    get() = GradleDaemonJvmCriteria(
      version = selectedVersion,
      vendor = selectedVendor.takeIf { it.isNotBlank() && it != ANY_VENDOR_PLACEHOLDER }
    )
  val isModified: Boolean
    get() = selectedVersion != initialVersion || selectedVendor != initialVendor
  val isValidVersion: Boolean
    get() = selectedVersion.toIntOrNull() != null
  val isValidVendor: Boolean
    get() = selectedVendor.isNotEmpty() && !selectedVendor.contains(" ")

  private var initialVersion: String = version ?: UNDEFINED_VERSION_PLACEHOLDER
  private var initialVendor: String = vendor ?: ANY_VENDOR_PLACEHOLDER
  private val selectedVersion: String
    get() = versionComboBox.editor.item.toString()
  private val selectedVendor: String
    get() = vendorComboBox.editor.item.toString()

  @get:VisibleForTesting
  lateinit var versionComboBox: ComboBox<String>
  @get:VisibleForTesting
  lateinit var vendorComboBox: ComboBox<String>

  private val component = panel {
    row {
      comment(GradleBundle.message("gradle.settings.text.daemon.toolchain.title")).applyToComponent {
        font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
        foreground = UIUtil.getLabelFontColor(UIUtil.FontColor.BRIGHTER)
      }
    }
    row {
      label(GradleBundle.message("gradle.settings.text.daemon.toolchain.version"))
      versionComboBox = comboBox(versionsDropdownList.map { it.toString() }.reversed(), textListCellRenderer { it })
        .columns(COLUMNS_SHORT)
        .cellValidation {
          addInputRule(GradleBundle.message("gradle.settings.text.daemon.toolchain.version.invalid")) { !isValidVersion }
          addApplyRule(GradleBundle.message("gradle.settings.text.daemon.toolchain.version.invalid")) { !isValidVersion }
        }
        .applyToComponent {
          selectAnyValue(initialVersion)
        }.component
    }
    collapsibleGroup(ApplicationBundle.message("title.advanced.settings")) {
      row {
        label(GradleBundle.message("gradle.settings.text.daemon.toolchain.vendor"))
        vendorComboBox = comboBox(listOf(ANY_VENDOR_PLACEHOLDER, CUSTOM_VENDOR_PLACEHOLDER) + vendorDropdownList, textListCellRenderer { it })
          .columns(COLUMNS_SHORT)
          .onChanged {
            if (it.selectedItem == CUSTOM_VENDOR_PLACEHOLDER) {
              it.selectAnyValue("", true)
            }
            else {
              it.isEditable = false
            }
          }
          .cellValidation {
            addInputRule(GradleBundle.message("gradle.settings.text.daemon.toolchain.vendor.invalid")) { !isValidVendor }
            addApplyRule(GradleBundle.message("gradle.settings.text.daemon.toolchain.vendor.invalid")) { !isValidVendor }
          }
          .applyToComponent {
            selectAnyValue(initialVendor)
          }.component
      }
      row {
        comment(GradleBundle.message("gradle.settings.text.daemon.toolchain.vendor.hint")).applyToComponent {
          font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
          foreground = UIUtil.getLabelFontColor(UIUtil.FontColor.BRIGHTER)
        }
      }
    }.topGap(TopGap.NONE)
      .visible(displayAdvancedSettings)
  }

  init {
    component.registerValidators(disposable)
    component.validateAll()

    add(component)
  }

  fun applySelection() {
    initialVersion = selectedVersion
    initialVendor = selectedVendor
  }

  fun resetSelection() {
    versionComboBox.selectAnyValue(initialVersion)
    vendorComboBox.selectAnyValue(initialVendor)
  }

  private fun <T> ComboBox<T>.selectAnyValue(value: Any, keepEditable: Boolean = false) {
    isEditable = true
    selectedItem = value
    isEditable = keepEditable
  }
}