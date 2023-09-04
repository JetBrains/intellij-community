package intellij.ide.startup.importSettings

import com.intellij.openapi.application.impl.ExternalSettingsImportScenario
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.panel

@Suppress("unused")
object SettingsImporter {

  @JvmStatic
  fun importSettings(): ExternalSettingsImportScenario {
    val panel = panel {
      row("Import test panel") {  }
    }
    val dialog = dialog("Import test", panel)
    dialog.show()
    return if (dialog.exitCode == 0)
      ExternalSettingsImportScenario.ImportedFromThirdPartyProduct
    else
      ExternalSettingsImportScenario.NoImport
  }
}