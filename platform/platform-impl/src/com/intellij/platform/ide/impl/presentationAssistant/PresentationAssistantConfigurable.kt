// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.presentationAssistant

import com.intellij.ide.IdeBundle
import com.intellij.openapi.components.service
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.DslConfigurableBase
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer

internal class PresentationAssistantConfigurable: DslConfigurableBase(), Configurable {
  override fun getDisplayName(): String = IdeBundle.message("presentation.assistant.settings")

  override fun createPanel(): DialogPanel {
    val configuration = service<PresentationAssistant>().configuration

    return panel {
      row {
        checkBox(IdeBundle.message("presentation.assistant.configurable.toggle"))
          .bindSelected(configuration::showActionDescriptions) { configuration.showActionDescriptions = it }
      }

      indent {
        panel {
          val comboGroup = "presentations_assistant_top_combo"

          if (PresentationAssistant.isThemeEnabled) {
            row(IdeBundle.message("presentation.assistant.configurable.theme")) {
              comboBox(CollectionComboBoxModel(PresentationAssistantTheme.entries, PresentationAssistantTheme.fromValueOrDefault(configuration.theme)),
                       textListCellRenderer { it?.displayName })
                .widthGroup(comboGroup)
                .bindItem({ PresentationAssistantTheme.fromValueOrDefault(configuration.theme) }) {
                  configuration.theme = it?.value
                }
            }
          }

          row(IdeBundle.message("presentation.assistant.configurable.popup.size")) {
            comboBox(CollectionComboBoxModel(PresentationAssistantPopupSize.entries, PresentationAssistantPopupSize.from(configuration.popupSize)),
                     textListCellRenderer { it?.displayName })
              .widthGroup(comboGroup)
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
                                             configuration.alignmentIfNoDelta),
                     textListCellRenderer { it?.displayName })
              .bindItem({ configuration.alignmentIfNoDelta }) {
                configuration.horizontalAlignment = it?.x ?: PresentationAssistantPopupAlignment.defaultAlignment.x
                configuration.verticalAlignment = it?.y ?: PresentationAssistantPopupAlignment.defaultAlignment.y
                configuration.resetDelta()
              }
          }
        }

        group(IdeBundle.message("presentation.assistant.configurable.keymap.group")) {
          row {
            val comboBox = comboBox(CollectionComboBoxModel(KeymapManagerEx.getInstanceEx().allKeymaps.toList(),
                                                            configuration.mainKeymapName.toKeymap()),
                                    textListCellRenderer { it?.presentableName }).label(IdeBundle.message("presentation.assistant.configurable.keymap.main"))
              .bindItem({ configuration.mainKeymapName.toKeymap() }) {
                configuration.mainKeymapName = it?.name ?: KeymapKind.defaultForOS().value
              }

            val label = textField()
              .label(IdeBundle.message("presentation.assistant.configurable.keymap.label"))
              .bindText(configuration::mainKeymapLabel) { configuration.mainKeymapLabel =  it }

            comboBox.onChanged {
              (it.selectedItem as? Keymap)?.let { keymap ->
                label.component.text = KeymapKind.from(keymap.name).defaultLabel
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

            val keymapKinds = mutableListOf<KeymapKind>()
            keymapKinds.addAll(KeymapManagerEx.getInstanceEx().allKeymaps.map { KeymapKind.from(it.name) })

            val defaultAlternativeKeymapKind = KeymapKind.defaultForOS().getAlternativeKind()
            if (!keymapKinds.contains(defaultAlternativeKeymapKind)) {
              keymapKinds.add(0, defaultAlternativeKeymapKind)
            }

            val alternativeKeymapKind = KeymapKind.from(configuration.alternativeKeymapName)
            if (!keymapKinds.contains(alternativeKeymapKind)) {
              keymapKinds.add(0, alternativeKeymapKind)
            }

            val comboBox = comboBox(CollectionComboBoxModel(keymapKinds, alternativeKeymapKind), textListCellRenderer { it?.displayName })
              .bindItem({ KeymapKind.from(configuration.alternativeKeymapName) }) {
                configuration.alternativeKeymapName = it?.value ?: KeymapKind.defaultForOS().getAlternativeKind().value
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
    service<PresentationAssistant>().updatePresenter()
  }

  override fun apply() {
    super.apply()
    service<PresentationAssistant>().updatePresenter()
  }
}

private fun String.toKeymap(): Keymap = KeymapManagerEx.getInstanceEx().let { it.getKeymap(this) ?: it.allKeymaps.first() }
