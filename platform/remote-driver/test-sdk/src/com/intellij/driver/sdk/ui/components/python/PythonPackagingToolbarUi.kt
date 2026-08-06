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
  x(xpath ?: "//div[@class='EditorHeaderComponent']", PythonPackagingToolbarUi::class.java)

class PythonPackagingToolbarUi(data: ComponentData) : UiComponent(data) {
  // Poetry actions. The update action is captioned "Poetry Update --sync" since PY-89521, so it is
  // matched by prefix to keep working against builds that still call it plain "Poetry Update".
  val poetryUpdateButton = x { contains(byAccessibleName("Poetry Update")) }
  val poetryLockButton = x { byAccessibleName("Poetry Lock") }

  // uv actions
  val uvLockButton = x { byAccessibleName("uv Lock") }
  val uvSyncButton = x { byAccessibleName("uv Sync") }

  // Conda actions
  val condaUpdateFromEnvYmlButton = x { byAccessibleName("Conda Update from \"environment.yml\"") }
  val condaExportToEnvYmlButton = x { byAccessibleName("Conda Export to \"environment.yml\"") }

  // Hatch actions
  val hatchRunSyncDependenciesButton = x { byAccessibleName("Hatch Run (Sync Dependencies)") }

  // Requirements actions
  val setAsEnvDependenciesButton = x { byAccessibleName("Set as the environment dependencies list") }
  /**
   * `PipUpdateEnvAction` depends on the surface it is rendered in: the
   * floating toolbar (`ActionPlaces.CONTEXT_TOOLBAR`) labels it "Install All", everywhere else it is `Pip Update from "<dependency file>"`.
   */
  val installAllButton = x {
    or(byAccessibleName("Install All"), contains(byAccessibleName("Pip Update")))
  }
}
