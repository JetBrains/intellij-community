// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.settings

import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.gradle.properties.models.Property
import org.jetbrains.plugins.gradle.util.GradleBundle
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.plaf.basic.BasicComboBoxEditor

private const val UNDEFINED_VERSION_PLACEHOLDER = "UNDEFINED"
private const val ANY_VENDOR_PLACEHOLDER = "<ANY_VENDOR>"

class GradleDaemonJvmCriteriaView(
  version: Property<String>?,
  vendor: Property<String>?,
  private val versionsDropdownList: IntRange,
  private val vendorDropdownList: List<String>,
  private val displayAdvancedSettings: Boolean
): JPanel(VerticalLayout(0)) {

  val selectedVersion: String
    get() = versionComboBox.editor.item.toString()
  val selectedVendor: String?
    get() = vendorComboBox.editor.item?.toString()?.takeIf { it.isNotBlank() || it != ANY_VENDOR_PLACEHOLDER }
  val isModified: Boolean
    get() = selectedVersion != initialVersion || selectedVendor != initialVendor
  val isValidVersion: Boolean
    get() = selectedVersion.toIntOrNull() != null
  val isValidVendor: Boolean
    get() = selectedVendor?.isNotBlank() == true

  private var initialVersion: String = version?.value ?: UNDEFINED_VERSION_PLACEHOLDER
  private var initialVendor: String = vendor?.value ?: ANY_VENDOR_PLACEHOLDER

  @get:VisibleForTesting
  lateinit var versionComboBox: ComboBox<Int>
  @get:VisibleForTesting
  lateinit var vendorComboBox: ComboBox<String>

  init {
    val gradleJvmView = panel {
      row {
        comment(GradleBundle.message("gradle.settings.text.daemon.toolchain.title")).applyToComponent {
          font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
          foreground = UIUtil.getLabelFontColor(UIUtil.FontColor.BRIGHTER)
        }
      }
      row {
        label(GradleBundle.message("gradle.settings.text.daemon.toolchain.version"))
        versionComboBox = comboBox(versionsDropdownList.toList().reversed(), textListCellRenderer { it.toString() })
          .columns(COLUMNS_SHORT)
          .applyToComponent {
            isEditable = true
            isFocusable = false
            editor = GradleJdkPathComboBoxEditor(this) { toIntOrNull() != null }
            editor.item = initialVersion
          }.component
      }
      collapsibleGroup(ApplicationBundle.message("title.advanced.settings")) {
        row {
          label(GradleBundle.message("gradle.settings.text.daemon.toolchain.vendor"))
          vendorComboBox = comboBox(listOf(ANY_VENDOR_PLACEHOLDER) + vendorDropdownList, textListCellRenderer { it })
            .columns(COLUMNS_SHORT)
            .applyToComponent {
              isEditable = true
              editor = GradleJdkPathComboBoxEditor(this) { isNotBlank() }
              editor.item = initialVendor
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
    add(gradleJvmView)
  }

  fun applySelection() {
    initialVersion = selectedVersion
    initialVendor = selectedVendor ?: ANY_VENDOR_PLACEHOLDER
  }

  fun resetSelection() {
    versionComboBox.selectedItem = initialVersion
    vendorComboBox.selectedItem = initialVendor
  }

  private class GradleJdkPathComboBoxEditor<T>(
    private val comboBox: ComboBox<T>,
    private val validateInput: String.() -> Boolean = { true }
  ) : BasicComboBoxEditor() {

    override fun createEditorComponent() = ExtendableTextField().apply {
      document.addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          validateSelectedJdkPath()
        }
      })
      border = null
    }

    private fun validateSelectedJdkPath() {
      comboBox.editor.editorComponent.apply {
        foreground = if (validateInput(comboBox.editor.item.toString())) JBColor.black else JBColor.red
      }
    }
  }
}