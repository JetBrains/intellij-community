package org.jetbrains.plugins.feature.suggester.settings

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ThreeStateCheckBox
import com.intellij.util.ui.ThreeStateCheckBox.State
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.border.EmptyBorder

private typealias ActionListener = (ActionEvent) -> Unit

class FeatureSuggestersPanel(
    suggestingActionNames: Iterable<String>,
    private val settings: FeatureSuggesterSettings
) : JPanel() {
    private val toggleAllCheckBox =
        ThreeStateCheckBox("Show suggestions for actions that I have not performed in more than ", State.SELECTED)
    private val suggestingIntervalField = JTextField(3)
    private val actionPanels: List<SuggestingActionPanel> = suggestingActionNames.map(::SuggestingActionPanel)

    init {
        layout = BorderLayout()
        add(createTopPanel(), BorderLayout.NORTH)
        add(createListPanel(), BorderLayout.WEST)
        loadFromSettings()
    }

    private fun createTopPanel(): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(0, 10, 0, 0)
        }
        val instructionLabel = JLabel(
            "Configure suggestions for actions. " +
                    "It will suggest the following actions in cases where their application can be effective."
        )
        panel.apply {
            add(instructionLabel)
            add(Box.createRigidArea(JBUI.size(0, 10)))
            add(createToggleAllPanel())
        }
        return panel
    }

    private fun createToggleAllPanel(): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            alignmentX = 0f
        }
        toggleAllCheckBox.isThirdStateEnabled = false
        toggleAllCheckBox.addActionListener {
            if (toggleAllCheckBox.state != State.DONT_CARE) {
                val selected = toggleAllCheckBox.isSelected
                actionPanels.forEach { it.select(selected) }
                suggestingIntervalField.isEnabled = selected
            }
        }

        suggestingIntervalField.maximumSize = Dimension(49, 30)
        val daysLabel = JBLabel(" days.")

        panel.apply {
            add(toggleAllCheckBox)
            add(suggestingIntervalField)
            add(daysLabel)
        }
        return panel
    }

    private fun createListPanel(): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(0, 10, 0, 0)
        }
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
                suggestingIntervalField.isEnabled = true
            } else if (anyNotSelected) {
                toggleAllCheckBox.isSelected = false
                suggestingIntervalField.isEnabled = false
            }
        }
    }

    fun loadFromSettings() {
        suggestingIntervalField.text = settings.suggestingIntervalDays.toString()
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
            if (somethingIsSelected) {
                toggleAllCheckBox.state = State.DONT_CARE
            } else {
                toggleAllCheckBox.state = State.NOT_SELECTED
                suggestingIntervalField.isEnabled = false
            }
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

    fun getSuggestingIntervalDays(): Int {
        val interval = suggestingIntervalField.text.toIntOrNull()
        return if (interval != null && interval >= 0) {
            interval
        } else {
            FeatureSuggesterSettings.DEFAULT_SUGGESTING_INTERVAL_DAYS
        }
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
