package org.editorconfig.settings

import com.intellij.application.options.GeneralCodeStyleOptionsProvider
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider
import com.intellij.psi.codeStyle.CustomCodeStyleSettings
import com.intellij.ui.dsl.builder.panel
import org.editorconfig.Utils
import org.editorconfig.language.messages.EditorConfigBundle
import javax.swing.JCheckBox
import javax.swing.JComponent

/**
 * @author Dennis.Ushakov
 */
class EditorConfigConfigurable : CodeStyleSettingsProvider(), GeneralCodeStyleOptionsProvider {
  private lateinit var myCbEnabled: JCheckBox

  override fun createComponent(): JComponent {
    return panel {
      row {
        myCbEnabled = checkBox(EditorConfigBundle.message("config.enable"))
          .comment(EditorConfigBundle.message("config.warning")).component

        if (EditorConfigExportProviderEP.shouldShowExportButton()) {
          button(EditorConfigBundle.message("config.export")) {
            val project = ProjectUtil.getProjectForComponent(myCbEnabled)
            if (project != null) {
              if (EditorConfigExportProviderEP.tryExportViaProviders(project)) return@button
              Utils.export(project)
            }
          }
        }
      }
    }
  }

  override fun isModified(settings: CodeStyleSettings): Boolean {
    return myCbEnabled.isSelected != settings.getCustomSettings(EditorConfigSettings::class.java).ENABLED
  }

  override fun apply(settings: CodeStyleSettings) {
    val newValue = myCbEnabled.isSelected
    settings.getCustomSettings(EditorConfigSettings::class.java).ENABLED = newValue
    val bus = ApplicationManager.getApplication().messageBus
    bus.syncPublisher(EditorConfigSettings.EDITOR_CONFIG_ENABLED_TOPIC).valueChanged(newValue)
  }

  override fun reset(settings: CodeStyleSettings) {
    myCbEnabled.isSelected = settings.getCustomSettings(EditorConfigSettings::class.java).ENABLED
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
