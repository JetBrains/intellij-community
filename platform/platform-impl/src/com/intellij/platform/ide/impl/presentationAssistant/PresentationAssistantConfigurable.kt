// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.presentationAssistant

import com.intellij.ide.IdeBundle
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.DslConfigurableBase
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer


class PresentationAssistantConfigurable: DslConfigurableBase(), Configurable {
  private val assistant: PresentationAssistant = PresentationAssistant.INSTANCE

  override fun getDisplayName(): String = IdeBundle.message("presentation.assistant.settings")

  override fun createPanel(): DialogPanel {
    val configuration = assistant.configuration

    return panel {
      row {
        checkBox(IdeBundle.message("presentation.assistant.configurable.toggle"))
          .bindSelected(configuration::showActionDescriptions) { configuration.showActionDescriptions = it }
      }

      indent {
        panel {
          row(IdeBundle.message("presentation.assistant.configurable.popup.size")) {
            comboBox(CollectionComboBoxModel(PresentationAssistantPopupSize.entries, PresentationAssistantPopupSize.from(configuration.popupSize)),
                     textListCellRenderer { it?.stringValue })
              .bindItem({ PresentationAssistantPopupSize.from(configuration.popupSize) }) {
                configuration.popupSize = it?.value ?: PresentationAssistantPopupSize.MEDIUM.value
              }
          }
          row(IdeBundle.message("presentation.assistant.configurable.duration")) {
            intTextField().bindIntText({ configuration.popupDuration / 1000 }) { configuration.popupDuration = it * 1000 }.gap(RightGap.SMALL)
            text(IdeBundle.message("presentation.assistant.configurable.duration.seconds"))
          }
          row(IdeBundle.message("presentation.assistant.configurable.popup.position")) {
            comboBox(CollectionComboBoxModel(PresentationAssistantPopupAlignment.entries,
                                             PresentationAssistantPopupAlignment.from(configuration.horizontalAlignment, configuration.verticalAlignment)),
                     textListCellRenderer { it?.stringValue })
              .bindItem({ PresentationAssistantPopupAlignment.from(configuration.horizontalAlignment, configuration.verticalAlignment) }) {
                configuration.horizontalAlignment = it?.x ?: PresentationAssistantPopupAlignment.defaultAlignment.x
                configuration.verticalAlignment = it?.y ?: PresentationAssistantPopupAlignment.defaultAlignment.y
              }
          }
        }

        group(IdeBundle.message("presentation.assistant.configurable.keymap.group")) {
          row {
            val comboBox = comboBox(CollectionComboBoxModel(KeymapKind.entries,
                                             KeymapKind.from(configuration.mainKeymap)),
                                    textListCellRenderer { it?.displayName }).label(IdeBundle.message("presentation.assistant.configurable.keymap.main"))
              .bindItem({ KeymapKind.from(configuration.mainKeymap) }) { configuration.mainKeymap = it?.value ?: defaultKeymapForOS().value }

            val label = textField()
              .label(IdeBundle.message("presentation.assistant.configurable.keymap.label"))
              .bindText(configuration::mainKeymapLabel) { configuration.mainKeymapLabel =  it }

            comboBox.onChanged {
              (it.selectedItem as? KeymapKind)?.let { kind ->
                label.component.text = kind.defaultLabel
              }
            }
          }

          row {
            val showAlternativeProperty = AtomicBooleanProperty(configuration.showAlternativeKeymap)

            checkBox(IdeBundle.message("presentation.assistant.configurable.keymap.additional"))
              .gap(RightGap.SMALL)
              .onChanged { showAlternativeProperty.set(it.isSelected) }
              .bindSelected(configuration::showAlternativeKeymap) {
                configuration.showAlternativeKeymap = it
              }

            val comboBox = comboBox(CollectionComboBoxModel(KeymapKind.entries,
                                             KeymapKind.from(configuration.alternativeKeymap)),
                                    textListCellRenderer { it?.displayName })
              .bindItem({ KeymapKind.from(configuration.alternativeKeymap) }) {
                configuration.alternativeKeymap = it?.value ?: defaultKeymapForOS().getAlternativeKind().value
              }.enabledIf(showAlternativeProperty)

            val label = textField()
              .label(IdeBundle.message("presentation.assistant.configurable.keymap.label"))
              .bindText(configuration::alternativeKeymapLabel) { configuration.alternativeKeymapLabel =  it }
              .enabledIf(showAlternativeProperty)

            comboBox.onChanged {
              (it.selectedItem as? KeymapKind)?.let { kind ->
                label.component.text = kind.defaultLabel
              }
            }
          }
        }
      }
    }
  }

  override fun reset() {
    super<DslConfigurableBase>.reset()
    PresentationAssistant.INSTANCE.updatePresenter()
  }

  override fun apply() {
    super.apply()
    PresentationAssistant.INSTANCE.updatePresenter()
  }
}