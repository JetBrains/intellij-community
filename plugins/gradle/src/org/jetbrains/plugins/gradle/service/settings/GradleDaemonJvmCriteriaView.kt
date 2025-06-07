// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ComboBoxWithEditableItem
import com.intellij.openapi.ui.ComboBoxWithEditableItem.EditableItem
import com.intellij.openapi.ui.ComboBoxWithEditableItem.SelectEditableItem
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.util.text.nullize
import com.intellij.util.ui.UIUtil
import org.gradle.internal.jvm.inspection.JvmVendor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmCriteria
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.toJvmVendor
import javax.swing.JPanel

@ApiStatus.Internal
class GradleDaemonJvmCriteriaView(
  criteria: GradleDaemonJvmCriteria,
  private val versionsDropdownList: List<Int>,
  private val vendorDropdownList: List<JvmVendor.KnownJvmVendor>,
  private val displayAdvancedSettings: Boolean,
  disposable: Disposable,
): JPanel(VerticalLayout(0)) {

  private var initialVersion: VersionItem? = when (val version = criteria.version) {
    null -> null
    else -> when (val knownVersion = version.toIntOrNull()) {
      null -> VersionItem.Custom(version)
      in versionsDropdownList -> VersionItem.Default(knownVersion)
      else -> VersionItem.Custom(version)
    }
  }

  private var initialVendor: VendorItem? = when (val vendor = criteria.vendor) {
    null -> VendorItem.Any
    else -> when (val knownVendor = vendor.knownVendor) {
      in vendorDropdownList -> VendorItem.Default(knownVendor)
      else -> VendorItem.Custom(vendor.rawVendor)
    }
  }

  val initialCriteria: GradleDaemonJvmCriteria
    get() = GradleDaemonJvmCriteria(
      version = initialVersion.let { version ->
        when (version) {
          null -> null
          is VersionItem.Default -> version.version.toString()
          is VersionItem.Custom -> version.value.trim().nullize()
        }
      },
      vendor = initialVendor.let { vendor ->
        when (vendor) {
          null -> null
          VendorItem.Any -> null
          VendorItem.SelectCustom -> null
          is VendorItem.Default -> vendor.vendor.asJvmVendor()
          is VendorItem.Custom -> vendor.value.trim().nullize()?.toJvmVendor()
        }
      }
    )

  @get:VisibleForTesting
  val isValidVersion: Boolean
    get() = selectedVersion.let { version ->
      when (version) {
        null -> false
        is VersionItem.Default -> true
        is VersionItem.Custom -> {
          version.value.trim().let { versionName ->
            versionName.toIntOrNull() != null
          }
        }
      }
    }

  @get:VisibleForTesting
  val isValidVendor: Boolean
    get() = selectedVendor.let { vendor ->
      when (vendor) {
        null -> true
        VendorItem.Any -> true
        VendorItem.SelectCustom -> false
        is VendorItem.Default -> true
        is VendorItem.Custom -> {
          vendor.value.trim().let { vendorName ->
            vendorName.isNotEmpty() && " " !in vendorName
          }
        }
      }
    }

  @VisibleForTesting
  val versionModel = CollectionComboBoxModel<VersionItem>().apply {
    for (version in versionsDropdownList.reversed()) {
      add(VersionItem.Default(version))
    }
  }

  @VisibleForTesting
  val vendorModel = CollectionComboBoxModel<VendorItem>().apply {
    add(VendorItem.Any)
    add(VendorItem.SelectCustom)
    for (vendor in vendorDropdownList) {
      add(VendorItem.Default(vendor))
    }
  }

  private val versionRenderer = textListCellRenderer<VersionItem?> { versionItem ->
    when (versionItem) {
      null -> GradleBundle.message("gradle.settings.text.daemon.toolchain.version.undefined")
      is VersionItem.Default -> versionItem.version.toString()
      is VersionItem.Custom -> versionItem.value
    }
  }

  private val vendorRenderer = textListCellRenderer<VendorItem?> { vendorItem ->
    when (vendorItem) {
      null -> GradleBundle.message("gradle.settings.text.daemon.toolchain.vendor.any")
      VendorItem.Any -> GradleBundle.message("gradle.settings.text.daemon.toolchain.vendor.any")
      VendorItem.SelectCustom -> GradleBundle.message("gradle.settings.text.daemon.toolchain.vendor.custom")
      is VendorItem.Default -> vendorItem.vendor.asJvmVendor().displayName
      is VendorItem.Custom -> vendorItem.value
    }
  }

  @get:VisibleForTesting
  @set:VisibleForTesting
  var selectedVersion: VersionItem?
    get() = versionComboBox.selectedItem as? VersionItem
    set(value) = versionComboBox.setSelectedItem(value)

  @get:VisibleForTesting
  @set:VisibleForTesting
  var selectedVendor: VendorItem?
    get() = vendorComboBox.selectedItem as? VendorItem
    set(value) = vendorComboBox.setSelectedItem(value)

  private lateinit var versionComboBox: ComboBox<VersionItem?>
  private lateinit var vendorComboBox: ComboBox<VendorItem?>

  private val component = panel {
    row {
      comment(GradleBundle.message("gradle.settings.text.daemon.toolchain.title")).applyToComponent {
        font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
        foreground = UIUtil.getLabelFontColor(UIUtil.FontColor.BRIGHTER)
      }
    }
    row(GradleBundle.message("gradle.settings.text.daemon.toolchain.version")) {
      versionComboBox = cell(ComboBoxWithEditableItem(versionModel, versionRenderer))
        .columns(COLUMNS_SHORT)
        .bindItem(::initialVersion)
        .cellValidation {
          addInputRule(GradleBundle.message("gradle.settings.text.daemon.toolchain.version.invalid")) { !isValidVersion }
          addApplyRule(GradleBundle.message("gradle.settings.text.daemon.toolchain.version.invalid")) { !isValidVersion }
        }.component
    }
    collapsibleGroup(ApplicationBundle.message("title.advanced.settings")) {
      row(GradleBundle.message("gradle.settings.text.daemon.toolchain.vendor")) {
        vendorComboBox = cell(ComboBoxWithEditableItem(vendorModel, vendorRenderer))
          .columns(COLUMNS_SHORT)
          .bindItem(::initialVendor)
          .cellValidation {
            addInputRule(GradleBundle.message("gradle.settings.text.daemon.toolchain.vendor.invalid")) { !isValidVendor }
            addApplyRule(GradleBundle.message("gradle.settings.text.daemon.toolchain.vendor.invalid")) { !isValidVendor }
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

  val isModified: Boolean
    get() = component.isModified()

  fun applySelection() {
    component.apply()
  }

  fun resetSelection() {
    component.reset()
  }

  @Throws(ConfigurationException::class)
  fun validateSelection() {
    if (!isValidVersion) {
      throw ConfigurationException(GradleBundle.message("gradle.settings.text.daemon.toolchain.version.error"))
    }
    if (!isValidVendor) {
      throw ConfigurationException(GradleBundle.message("gradle.settings.text.daemon.toolchain.vendor.error"))
    }
  }

  @VisibleForTesting
  sealed interface VersionItem {
    data class Default(val version: Int) : VersionItem
    data class Custom(val value: String) : VersionItem, EditableItem {
      override fun valueOf(value: String): Custom = Custom(value)
      override fun toString(): String = value
    }
  }

  @VisibleForTesting
  sealed interface VendorItem {
    object Any : VendorItem
    object SelectCustom : VendorItem, SelectEditableItem {
      override fun createItem(): Custom = Custom("")
    }
    data class Default(val vendor: JvmVendor.KnownJvmVendor) : VendorItem
    data class Custom(val value: String) : VendorItem, EditableItem {
      override fun valueOf(value: String): Custom = Custom(value)
      override fun toString(): String = value
    }
  }
}