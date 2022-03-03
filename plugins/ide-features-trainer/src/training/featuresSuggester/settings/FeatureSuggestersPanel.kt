package training.featuresSuggester.settings

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ThreeStateCheckBox
import com.intellij.util.ui.ThreeStateCheckBox.State
import org.jetbrains.annotations.Nls
import training.featuresSuggester.FeatureSuggesterBundle
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.border.EmptyBorder

private typealias ActionListener = (ActionEvent) -> Unit

@Suppress("MagicNumber", "DialogTitleCapitalization")
class FeatureSuggestersPanel(
  suggesterIdToName: Map<String, String>,
  private val settings: FeatureSuggesterSettings
) : JPanel() {
  private val toggleAllCheckBox =
    ThreeStateCheckBox(FeatureSuggesterBundle.message("configurable.show.suggestions.checkbox"), State.SELECTED)
  private val suggestingIntervalField = JTextField(3)
  private val actionPanels: List<SuggestingActionPanel> =
    suggesterIdToName.map { SuggestingActionPanel(it.key, it.value) }

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
    val instructionLabel =
      JLabel("<html><body>${FeatureSuggesterBundle.message("configurable.explanation")}</body></html>")
    instructionLabel.maximumSize = Dimension(580, 50)
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
    val daysLabel = JBLabel(FeatureSuggesterBundle.message("configurable.days.label"))

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
    val listener: ActionListener = { doWithActionPanels() }
    actionPanels.forEach {
      with(it) {
        alignmentX = 0f
        border = EmptyBorder(1, 17, 3, 1)
        addActionListener(listener)
      }
    }
  }

  private fun doWithActionPanels(action: (SuggestingActionPanel) -> Unit = {}) {
    var anySelected = false
    var anyNotSelected = false
    actionPanels.forEach {
      action(it)
      if (it.selected()) {
        anySelected = true
      }
      else {
        anyNotSelected = true
      }
    }
    if (anySelected && anyNotSelected) {
      toggleAllCheckBox.state = State.DONT_CARE
      suggestingIntervalField.isEnabled = true
    }
    else if (anySelected) {
      toggleAllCheckBox.isSelected = true
      suggestingIntervalField.isEnabled = true
    }
    else if (anyNotSelected) {
      toggleAllCheckBox.isSelected = false
      suggestingIntervalField.isEnabled = false
    }
  }

  fun loadFromSettings() {
    suggestingIntervalField.text = settings.suggestingIntervalDays.toString()
    doWithActionPanels { panel ->
      val enabled = settings.isEnabled(panel.suggesterId)
      panel.select(enabled)
    }
  }

  fun isSelected(suggesterId: String): Boolean {
    val panel = actionPanels.find { it.suggesterId == suggesterId }
                ?: throw IllegalArgumentException("Unknown action name: $suggesterId")
    return panel.selected()
  }

  fun getSuggestingIntervalDays(): Int {
    val interval = suggestingIntervalField.text.toIntOrNull()
    return if (interval != null && interval >= 0) {
      interval
    }
    else {
      FeatureSuggesterSettings.DEFAULT_SUGGESTING_INTERVAL_DAYS
    }
  }

  private class SuggestingActionPanel(val suggesterId: String, actionDisplayName: @Nls String) : JPanel() {
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
