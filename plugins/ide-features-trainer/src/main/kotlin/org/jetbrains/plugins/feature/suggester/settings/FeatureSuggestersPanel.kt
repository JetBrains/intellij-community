package org.jetbrains.plugins.feature.suggester.settings

import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ThreeStateCheckBox
import com.intellij.util.ui.ThreeStateCheckBox.State
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.border.EmptyBorder

private typealias ActionListener = (ActionEvent) -> Unit

class FeatureSuggestersPanel(
    suggestingActionNames: Iterable<String>,
    private val settings: FeatureSuggesterSettings
) : JPanel() {
    private val toggleAllCheckBox = ThreeStateCheckBox("Show suggestions for:", State.SELECTED)
    private val actionPanels: List<SuggestingActionPanel> = suggestingActionNames.map(::SuggestingActionPanel)

    init {
        layout = BorderLayout()
        val label =
            JLabel("Configure suggestions for actions. It will suggest the following actions in cases where their application can be effective.")
        add(label, BorderLayout.NORTH)
        add(createListPanel(), BorderLayout.WEST)
        loadFromSettings()
    }

    private fun createListPanel(): JPanel {
        val panel = JPanel()
        panel.apply {
            layout = BoxLayout(panel, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(0, 10, 0, 0)
            add(Box.createRigidArea(JBUI.size(0, 10)))
        }
        toggleAllCheckBox.isThirdStateEnabled = false
        toggleAllCheckBox.addActionListener {
            if (toggleAllCheckBox.state != State.DONT_CARE) {
                val selected = toggleAllCheckBox.isSelected
                actionPanels.forEach { it.select(selected) }
            }
        }
        panel.add(toggleAllCheckBox)
        configureActionPanels()
        actionPanels.forEach { panel.add(it) }

        return panel
    }

    private fun configureActionPanels() {
        val listener = createActionPanelListener()
        actionPanels.forEach {
            with(it) {
                alignmentX = 0f
                border = EmptyBorder(1, 17, 3, 1)
                addActionListener(listener)
            }
        }
    }

    private fun createActionPanelListener(): ActionListener {
        return {
            var anySelected = false
            var anyNotSelected = false
            actionPanels.forEach {
                if (it.selected()) {
                    anySelected = true
                } else {
                    anyNotSelected = true
                }
            }
            if (anySelected && anyNotSelected) {
                toggleAllCheckBox.state = State.DONT_CARE
            } else if (anySelected) {
                toggleAllCheckBox.isSelected = true
            } else if (anyNotSelected) {
                toggleAllCheckBox.isSelected = false
            }
        }
    }

    fun loadFromSettings() {
        if (settings.isAllEnabled()) {
            toggleAllCheckBox.isSelected = true
            actionPanels.forEach { it.select(true) }
        } else {
            var somethingIsSelected = false
            actionPanels.forEach {
                val selected = settings.isEnabled(it.actionDisplayName)
                if (selected) somethingIsSelected = true
                it.select(selected)
            }
            toggleAllCheckBox.state = if (somethingIsSelected) State.DONT_CARE else State.NOT_SELECTED
        }
    }

    fun isAllSelected(): Boolean {
        return toggleAllCheckBox.isSelected
    }

    fun isSelected(suggestingActionName: String): Boolean {
        val panel = actionPanels.find { it.actionDisplayName == suggestingActionName }
            ?: throw IllegalArgumentException("Unknown action name: $suggestingActionName")
        return panel.selected()
    }

    private class SuggestingActionPanel(val actionDisplayName: String) : JPanel() {
        private val checkBox = JCheckBox(actionDisplayName)

        init {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(checkBox)
        }

        fun selected(): Boolean {
            return checkBox.isSelected
        }

        fun select(value: Boolean) {
            checkBox.isSelected = value
        }

        fun addActionListener(listener: ActionListener) {
            checkBox.addActionListener(listener)
        }
    }
}
