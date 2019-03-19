package org.editorconfig.settings

import com.intellij.application.options.GeneralCodeStyleOptionsProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.wm.IdeFrame
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider
import com.intellij.psi.codeStyle.CustomCodeStyleSettings
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.messages.MessageBus
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.editorconfig.Utils
import org.editorconfig.language.messages.EditorConfigBundle

import javax.swing.*
import java.awt.*

/**
 * @author Dennis.Ushakov
 */
class EditorConfigConfigurable : CodeStyleSettingsProvider(), GeneralCodeStyleOptionsProvider {
  private var myEnabled: JBCheckBox? = null

  override fun createComponent(): JComponent? {
    myEnabled = JBCheckBox(EditorConfigBundle.message("config.enable"))
    val result = JPanel()
    result.layout = BoxLayout(result, BoxLayout.LINE_AXIS)
    val panel = JPanel(VerticalFlowLayout())
    result.border = IdeBorderFactory.createTitledBorder(EditorConfigBundle.message("config.title"), false)
    panel.add(myEnabled)
    val warning = JLabel(EditorConfigBundle.message("config.warning"))
    warning.font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
    warning.border = JBUI.Borders.emptyLeft(20)
    panel.add(warning)
    panel.alignmentY = Component.TOP_ALIGNMENT
    result.add(panel)
    val export = JButton(EditorConfigBundle.message("config.export"))
    // export.setVisible(EditorConfigExportProviderEP.shouldShowExportButton());
    export.addActionListener { event ->
      val parent = UIUtil.findUltimateParent(result)

      if (parent is IdeFrame) {
        val project = (parent as IdeFrame).project
        if (project != null) {
          if (EditorConfigExportProviderEP.tryExportViaProviders(project)) return@addActionListener
          Utils.export(project)
        }
      }
    }
    export.alignmentY = Component.TOP_ALIGNMENT
    result.add(export)
    return result
  }

  override fun isModified(settings: CodeStyleSettings): Boolean {
    return myEnabled!!.isSelected != settings.getCustomSettings(EditorConfigSettings::class.java).ENABLED
  }

  override fun apply(settings: CodeStyleSettings) {
    val newValue = myEnabled!!.isSelected
    settings.getCustomSettings(EditorConfigSettings::class.java).ENABLED = newValue
    val bus = ApplicationManager.getApplication().messageBus
    bus.syncPublisher(EditorConfigSettings.EDITOR_CONFIG_ENABLED_TOPIC).valueChanged(newValue)
  }

  override fun reset(settings: CodeStyleSettings) {
    myEnabled!!.isSelected = settings.getCustomSettings(EditorConfigSettings::class.java).ENABLED
  }

  override fun disposeUIResources() {
    myEnabled = null
  }

  override fun isModified(): Boolean = false

  @Throws(ConfigurationException::class)
  override fun apply() {
  }

  override fun hasSettingsPage(): Boolean = false

  override fun createCustomSettings(settings: CodeStyleSettings): CustomCodeStyleSettings? {
    return EditorConfigSettings(settings)
  }
}
