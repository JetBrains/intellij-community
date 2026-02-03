package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import org.intellij.lang.annotations.Language

fun Finder.pythonEditorFloatingToolbar(@Language("xpath") xpath: String? = null) =
  x(xpath ?: "//div[@class='EditorFloatingToolbar']", PythonEditorFloatingToolbarUi::class.java)

class PythonEditorFloatingToolbarUi(data: ComponentData) : UiComponent(data) {
  val poetryUpdateButton = x { byAccessibleName("Poetry Update") }
  val poetryLockButton = x { byAccessibleName("Poetry Lock") }

  // uv floating toolbar actions
  val uvLockButton = x { byAccessibleName("uv Lock") }
  val uvSyncButton = x { byAccessibleName("uv Sync") }

  // Conda floating toolbar actions
  val condaUpdateFromEnvYmlButton = x { byAccessibleName("Conda Update from \"environment.yml\"") }
  val condaExportToEnvYmlButton = x { byAccessibleName("Conda Export to \"environment.yml\"") }

  // Hatch floating toolbar actions
  val hatchRunSyncDependenciesButton = x { byAccessibleName("Hatch Run (Sync Dependencies)") }

  // Requirements floating toolbar actions
  val setAsEnvDependenciesButton = x { byAccessibleName("Set as the environment dependencies list") }
  val installAllButton = x { byAccessibleName("Install All") }
}
