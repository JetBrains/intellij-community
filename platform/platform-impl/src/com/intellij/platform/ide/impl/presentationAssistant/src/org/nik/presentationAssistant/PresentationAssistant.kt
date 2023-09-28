/*
 * Copyright 2000-2016 Nikolay Chashnikov.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @author nik
 */
package org.nik.presentationAssistant

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import com.intellij.util.xmlb.XmlSerializerUtil
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*

class PresentationAssistantState {
    var showActionDescriptions = true
    var fontSize = 24
    var hideDelay = 4*1000
    var mainKeymap = getDefaultMainKeymap()
    var alternativeKeymap = getDefaultAlternativeKeymap()
    var horizontalAlignment = PopupHorizontalAlignment.CENTER
    var verticalAlignment = PopupVerticalAlignment.BOTTOM
    var margin = 5
}

enum class PopupHorizontalAlignment(val displayName: String) { LEFT("Left"), CENTER("Center"), RIGHT("Right") }
enum class PopupVerticalAlignment(val displayName: String) { TOP("Top"), BOTTOM("Bottom") }

@State(name = "PresentationAssistant", storages = [Storage(file = "presentation-assistant.xml")])
class PresentationAssistant : PersistentStateComponent<PresentationAssistantState>, Disposable {
    val configuration = PresentationAssistantState()
    var warningAboutMacKeymapWasShown = false
    private var presenter: ShortcutPresenter? = null

    override fun getState() = configuration
    override fun loadState(p: PresentationAssistantState) {
        XmlSerializerUtil.copyBean(p, configuration)
    }

    fun initialize() {
        if (configuration.showActionDescriptions && presenter == null) {
            presenter = ShortcutPresenter()
        }
    }

    override fun dispose() {
        presenter?.disable()
    }

    fun setShowActionsDescriptions(value: Boolean, project: Project?) {
        configuration.showActionDescriptions = value
        if (value && presenter == null) {
            presenter = ShortcutPresenter().apply {
                showActionInfo(ShortcutPresenter.ActionData("presentationAssistant.ShowActionDescriptions", project, "Show Descriptions of Actions"))
            }
        }
        if (!value && presenter != null) {
            presenter?.disable()
            presenter = null
        }
    }

    fun checkIfMacKeymapIsAvailable() {
        val alternativeKeymap = configuration.alternativeKeymap
        if (warningAboutMacKeymapWasShown || getCurrentOSKind() == KeymapKind.MAC || alternativeKeymap == null) {
            return
        }
        if (alternativeKeymap.displayText != "for Mac" || alternativeKeymap.getKeymap() != null) {
            return
        }

        val pluginId = PluginId.getId("com.intellij.plugins.macoskeymap")
        val plugin = PluginManagerCore.getPlugin(pluginId)
        if (plugin != null && plugin.isEnabled) return

        warningAboutMacKeymapWasShown = true
        showInstallMacKeymapPluginNotification(pluginId)
    }

    fun setFontSize(value: Int) {
        configuration.fontSize = value
    }

    fun setHideDelay(value: Int) {
        configuration.hideDelay = value
    }
}

fun getPresentationAssistant(): PresentationAssistant = ApplicationManager.getApplication().getService(PresentationAssistant::class.java)

class PresentationAssistantListenerRegistrar : AppLifecycleListener, DynamicPluginListener {
    override fun appFrameCreated(commandLineArgs: MutableList<String>) {
        getPresentationAssistant().initialize()
    }

    override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
        if (pluginDescriptor.pluginId.idString == "org.nik.presentation-assistant") {
            getPresentationAssistant().initialize()
        }
    }
}

class KeymapDescriptionPanel {
    private val combobox = ComboBox(KeymapManagerEx.getInstanceEx().allKeymaps)
    private val text = JTextField(10)
    val mainPanel: JPanel
    init
    {
        combobox.renderer = object: SimpleListCellRenderer<Keymap>() {
            override fun customize(list: JList<out Keymap>, value: Keymap?, index: Int, selected: Boolean, hasFocus: Boolean) {
                text = value?.presentableName ?: ""
            }
        }
        val formBuilder = FormBuilder.createFormBuilder()
                .setFormLeftIndent(20)
                .addLabeledComponent("Keymap:", combobox)
                .addLabeledComponent("Description:", text)
        mainPanel = formBuilder.panel
    }

    fun getDescription() = KeymapDescription((combobox.selectedItem as Keymap?)?.name ?: "", text.text)

    fun setEnabled(enabled: Boolean) {
        UIUtil.setEnabled(mainPanel, enabled, true)
    }

    fun reset(config: KeymapDescription) {
        combobox.selectedItem = KeymapManager.getInstance().getKeymap(config.name)
        text.text = config.displayText
    }
}

class PresentationAssistantConfigurable : Configurable, SearchableConfigurable {
    private val configuration: PresentationAssistant = getPresentationAssistant()
    private val showAltKeymap = JCheckBox("Alternative Keymap:")
    private val mainKeymapPanel = KeymapDescriptionPanel()
    private val altKeymapPanel = KeymapDescriptionPanel()
    private val fontSizeField = JTextField(5)
    private val hideDelayField = JTextField(5)
    private val horizontalAlignmentButtons = PopupHorizontalAlignment.values().associateWith { JRadioButton(it.displayName) }
    private val verticalAlignmentButtons = PopupVerticalAlignment.values().associateWith { JRadioButton(it.displayName) }
    private val marginField = JTextField(5)

    private val mainPanel: JPanel
    init
    {
        val horizontalAlignmentPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            horizontalAlignmentButtons.values.forEach { add(it) }
        }
        val verticalAlignmentPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            verticalAlignmentButtons.values.forEach { add(it) }
        }
        ButtonGroup().apply {
            horizontalAlignmentButtons.values.forEach { add(it) }
        }
        ButtonGroup().apply {
            verticalAlignmentButtons.values.forEach { add(it) }
        }

        val formBuilder = FormBuilder.createFormBuilder()
                           .addLabeledComponent("&Font size:", fontSizeField)
                           .addLabeledComponent("&Display duration (in ms):", hideDelayField)
                           .addLabeledComponent("Horizontal alignment:", horizontalAlignmentPanel, 0)
                           .addLabeledComponent("Vertical alignment:", verticalAlignmentPanel, 0)
                           .addLabeledComponent("Margin:", marginField, 0)
                           .addVerticalGap(10)
                           .addLabeledComponent("Main Keymap:", mainKeymapPanel.mainPanel, true)
                           .addLabeledComponent(showAltKeymap, altKeymapPanel.mainPanel, true)
        showAltKeymap.addActionListener {
            altKeymapPanel.setEnabled(showAltKeymap.isSelected)
        }
        mainPanel = JPanel(BorderLayout())
        mainPanel.add(BorderLayout.NORTH, formBuilder.panel)
    }

    private fun updatePanels() {
        altKeymapPanel.setEnabled(showAltKeymap.isSelected)
    }

    override fun getId() = displayName
    override fun enableSearch(option: String?): Runnable? = null
    override fun getDisplayName() = "Presentation Assistant"
    override fun getHelpTopic(): String? = null

    override fun createComponent() = mainPanel
    override fun isModified() = isDigitsOnly(fontSizeField.text) && (fontSizeField.text != configuration.configuration.fontSize.toString())
                                || isDigitsOnly(hideDelayField.text) && (hideDelayField.text != configuration.configuration.hideDelay.toString())
                                || configuration.configuration.mainKeymap != mainKeymapPanel.getDescription()
                                || configuration.configuration.alternativeKeymap != getAlternativeKeymap()
                                || !horizontalAlignmentButtons[configuration.configuration.horizontalAlignment]!!.isSelected
                                || !verticalAlignmentButtons[configuration.configuration.verticalAlignment]!!.isSelected
                                || isDigitsOnly(marginField.text) && (marginField.text != configuration.configuration.margin.toString())

    private fun getAlternativeKeymap() = if (showAltKeymap.isSelected) altKeymapPanel.getDescription() else null

    override fun apply() {
        configuration.setFontSize(fontSizeField.text.trim().toInt())
        configuration.setHideDelay(hideDelayField.text.trim().toInt())
        configuration.configuration.mainKeymap = mainKeymapPanel.getDescription()
        configuration.configuration.alternativeKeymap = getAlternativeKeymap()
        configuration.configuration.horizontalAlignment = horizontalAlignmentButtons.entries.find { it.value.isSelected }!!.key
        configuration.configuration.verticalAlignment = verticalAlignmentButtons.entries.find { it.value.isSelected }!!.key
        configuration.configuration.margin = marginField.text.trim().toInt()
    }

    override fun reset() {
        fontSizeField.text = configuration.configuration.fontSize.toString()
        hideDelayField.text = configuration.configuration.hideDelay.toString()
        showAltKeymap.isSelected = configuration.configuration.alternativeKeymap != null
        mainKeymapPanel.reset(configuration.configuration.mainKeymap)
        altKeymapPanel.reset(configuration.configuration.alternativeKeymap ?: KeymapDescription("", ""))
        horizontalAlignmentButtons.forEach { (value, button) -> button.isSelected = configuration.configuration.horizontalAlignment == value }
        verticalAlignmentButtons.forEach { (value, button) -> button.isSelected = configuration.configuration.verticalAlignment == value }
        marginField.text = configuration.configuration.margin.toString()
        updatePanels()
    }

    override fun disposeUIResources() {
    }

    private fun isDigitsOnly(string: String): Boolean {
        return string.all { c -> c.isDigit() }
    }
}