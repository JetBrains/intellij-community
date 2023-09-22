package intellij.ide.startup.importSettings

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.IdeStartupWizard
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.panel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Suppress("unused") // instantiated by reflection
class IdeStartupWizardImpl : IdeStartupWizard {

  @Suppress("HardCodedStringLiteral") // temporary
  override suspend fun run() {
    withContext(Dispatchers.EDT) {
      val panel = panel {
        row("Import test panel") {  }
      }
      val dialog = dialog("Import Test", panel)
      dialog.show()
    }
  }
}