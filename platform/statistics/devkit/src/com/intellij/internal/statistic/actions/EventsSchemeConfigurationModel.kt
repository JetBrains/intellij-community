// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions

import com.intellij.internal.statistic.eventLog.getEventLogProviders
import com.intellij.internal.statistic.eventLog.validator.persistence.BaseEventLogWhitelistPersistence.getDefaultMetadataFile
import com.intellij.internal.statistic.eventLog.validator.persistence.EventLogWhitelistPersistence.EVENTS_SCHEME_FILE
import com.intellij.internal.statistic.eventLog.validator.persistence.EventLogWhitelistSettingsPersistence
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.layout.*
import javax.swing.JCheckBox
import javax.swing.event.DocumentEvent

class EventsSchemeConfigurationModel {
  val panel: DialogPanel
  val recorderToSettings: MutableMap<String, WhitelistPathSettings> = mutableMapOf()
  private val recorderComboBox = ComboBox<String>()
  private val pathField = TextFieldWithBrowseButton()
  private val useCustomPathCheckBox: JCheckBox = JCheckBox("Use custom path:")
  private var currentSettings: WhitelistPathSettings? = null

  init {
    getEventLogProviders().forEach { provider ->
      val recorderId = provider.recorderId
      recorderComboBox.addItem(recorderId)
    }
    recorderComboBox.addActionListener { updatePanel() }

    pathField.addBrowseFolderListener("Select Events Scheme Location ", null, null, FileChooserDescriptorFactory.createSingleFileDescriptor())
    pathField.textField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        if (useCustomPathCheckBox.isSelected) {
          currentSettings?.customPath = pathField.text
        }
      }
    })

    useCustomPathCheckBox.addActionListener {
      currentSettings?.useCustomPath = useCustomPathCheckBox.isSelected
      updatePathField()
    }
    updatePanel()

    panel = panel {
      row {
        label("Recorder:")
        recorderComboBox(growX, pushX)
      }
      row {
        useCustomPathCheckBox()
        pathField(growX, pushX)
      }
    }
  }

  private fun updatePathField() {
    val useCustomPath = useCustomPathCheckBox.isSelected
    val settings = currentSettings
    if (settings == null) return

    pathField.isEditable = useCustomPath
    pathField.isEnabled = useCustomPath

    pathField.text = settings.currentPath
  }

  private fun updatePanel() {
    val recorderId = recorderComboBox.selectedItem as String
    val settings = recorderToSettings.computeIfAbsent(recorderId) { WhitelistPathSettings(recorderId) }

    currentSettings = settings
    useCustomPathCheckBox.isSelected = settings.useCustomPath
    updatePathField()
  }

  fun validate(): ValidationInfo? {
    val currentPathSettings = currentSettings ?: return null
    val currentValidationInfo = validatePath(currentPathSettings)
    if (currentValidationInfo != null) {
      return currentValidationInfo
    }

    for ((recorder, settings) in recorderToSettings) {
      if (settings == currentSettings) continue
      val validationInfo = validatePath(settings)
      if (validationInfo != null) {
        recorderComboBox.selectedItem = recorder
        updatePanel()
        return validationInfo
      }
    }

    return null
  }

  private fun validatePath(settings: WhitelistPathSettings): ValidationInfo? {
    if (!settings.useCustomPath) return null

    val customPath = settings.customPath
    if (customPath.isNullOrBlank()) {
      return ValidationInfo("Specify events scheme path.", pathField.textField)
    }
    else if (!FileUtil.exists(customPath)) {
      return ValidationInfo("File doesn't exist.", pathField.textField)
    }
    return null
  }

  class WhitelistPathSettings(recorderId: String) {
    private val defaultPath: String = getDefaultMetadataFile(recorderId, EVENTS_SCHEME_FILE, null).absolutePath
    var customPath: String? = null
    var useCustomPath = false

    init {
      val pathSettings = EventLogWhitelistSettingsPersistence.getInstance().getPathSettings(recorderId)
      if (pathSettings != null) {
        customPath = pathSettings.customPath
        useCustomPath = pathSettings.isUseCustomPath
      }
    }

    val currentPath: String
      get() {
        val customEventsSchemePath = customPath
        return if (useCustomPath && customEventsSchemePath != null) {
          customEventsSchemePath
        }
        else {
          defaultPath
        }
      }
  }

}