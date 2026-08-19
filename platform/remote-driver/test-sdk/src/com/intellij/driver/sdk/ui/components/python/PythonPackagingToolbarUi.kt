package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import org.intellij.lang.annotations.Language

/**
 * Toolbar pinned to the top of a dependency-file editor (pyproject.toml, environment.yml, Pipfile,
 * requirements.txt, ...) hosting the Python package-manager actions - `PyPackageManagerEditorBanner`.
 */
fun Finder.pythonPackagingEditorToolbar(@Language("xpath") xpath: String? = null) =
  x(xpath ?: "//div[@class='EditorHeaderComponent']", PythonPackagingToolbarUi::class.java, "'Python packaging' editor toolbar")

class PythonPackagingToolbarUi(data: ComponentData) : UiComponent(data) {
  // Poetry actions. The update action is captioned "Poetry Update --sync" since PY-89521, so it is
  // matched by prefix to keep working against builds that still call it plain "Poetry Update".
  val poetryUpdateButton = x("'Poetry Update' action") { contains(byAccessibleName("Poetry Update")) }
  val poetryLockButton = x("'Poetry Lock' action") { byAccessibleName("Poetry Lock") }

  // uv actions
  val uvLockButton = x("'uv Lock' action") { byAccessibleName("uv Lock") }
  val uvSyncButton = x("'uv Sync' action") { byAccessibleName("uv Sync") }

  // Conda actions
  val condaUpdateFromEnvYmlButton = x("'Conda Update from \"environment.yml\"' action") {
    byAccessibleName("Conda Update from \"environment.yml\"")
  }
  val condaExportToEnvYmlButton = x("'Conda Export to \"environment.yml\"' action") {
    byAccessibleName("Conda Export to \"environment.yml\"")
  }

  // Hatch actions
  val hatchRunSyncDependenciesButton = x("'Hatch Run (Sync Dependencies)' action") {
    byAccessibleName("Hatch Run (Sync Dependencies)")
  }

  // Requirements actions
  val setAsEnvDependenciesButton = x("'Set as the environment dependencies list' action") {
    byAccessibleName("Set as the environment dependencies list")
  }
  /**
   * `PipUpdateEnvAction` depends on the surface it is rendered in: the
   * floating toolbar (`ActionPlaces.CONTEXT_TOOLBAR`) labels it "Install All", everywhere else it is `Pip Update from "<dependency file>"`.
   */
  val installAllButton = x("'Install All' / 'Pip Update' action") {
    or(byAccessibleName("Install All"), contains(byAccessibleName("Pip Update")))
  }
}
