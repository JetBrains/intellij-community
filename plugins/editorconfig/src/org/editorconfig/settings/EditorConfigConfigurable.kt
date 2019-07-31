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
import com.intellij.ui.layout.*
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
  private lateinit var myEnabled: JCheckBox

  override fun createComponent(): JComponent {
    return panel {
      row {
        myEnabled = checkBox(EditorConfigBundle.message("config.enable"), comment = EditorConfigBundle.message("config.warning"))

        if (EditorConfigExportProviderEP.shouldShowExportButton()) {
          button(EditorConfigBundle.message("config.export")) {
            val parent = UIUtil.findUltimateParent(myEnabled)

            if (parent is IdeFrame) {
              val project = (parent as IdeFrame).project
              if (project != null) {
                if (EditorConfigExportProviderEP.tryExportViaProviders(project)) return@button
                Utils.export(project)
              }
            }
          }
        }
      }
    }
  }

  override fun isModified(settings: CodeStyleSettings): Boolean {
    return myEnabled.isSelected != settings.getCustomSettings(EditorConfigSettings::class.java).ENABLED
  }

  override fun apply(settings: CodeStyleSettings) {
    val newValue = myEnabled.isSelected
    settings.getCustomSettings(EditorConfigSettings::class.java).ENABLED = newValue
    val bus = ApplicationManager.getApplication().messageBus
    bus.syncPublisher(EditorConfigSettings.EDITOR_CONFIG_ENABLED_TOPIC).valueChanged(newValue)
  }

  override fun reset(settings: CodeStyleSettings) {
    myEnabled.isSelected = settings.getCustomSettings(EditorConfigSettings::class.java).ENABLED
  }

  override fun disposeUIResources() {
  }

  override fun isModified(): Boolean = false

  override fun apply() {
  }

  override fun hasSettingsPage(): Boolean = false

  override fun createCustomSettings(settings: CodeStyleSettings): CustomCodeStyleSettings {
    return EditorConfigSettings(settings)
  }
}
