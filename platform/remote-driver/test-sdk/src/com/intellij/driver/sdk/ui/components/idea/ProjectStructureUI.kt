package com.intellij.driver.sdk.ui.components.idea

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent
import com.intellij.driver.sdk.ui.components.elements.accessibleList

fun Finder.projectStructure(action: ProjectStructureUI.() -> Unit) {
  x(ProjectStructureUI::class.java) { byTitle("Project Structure") }.action()
}

open class ProjectStructureUI(data: ComponentData) : DialogUiComponent(data) {

  private fun selectItemFromProjectStructure(itemTitle: String) {
    return accessibleList().clickItem(itemTitle)
  }

  fun openSdkSettings() {
    selectItemFromProjectStructure("SDKs")
  }

  fun openModuleSettings() {
    selectItemFromProjectStructure("Modules")
  }

  fun openArtifactsSettings() {
    selectItemFromProjectStructure("Artifacts")
  }

  fun openFacetsSettings() {
    selectItemFromProjectStructure("Facets")
  }

  fun openProjectSettings() {
    selectItemFromProjectStructure("Project")
  }
}