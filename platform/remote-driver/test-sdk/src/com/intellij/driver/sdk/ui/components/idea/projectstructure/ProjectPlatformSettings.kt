package com.intellij.driver.sdk.ui.components.idea.projectstructure

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.JTreeUiComponent
import com.intellij.driver.sdk.ui.components.elements.textField
import com.intellij.driver.sdk.ui.components.elements.tree
import com.intellij.driver.sdk.ui.components.idea.ProjectStructureUI

fun ProjectStructureUI.projectSettings(action: ProjectSettingsSectionUI.() -> Unit) {
  x("//div[@accessiblename='Project']/parent::div[@class='DialogPanel']", ProjectSettingsSectionUI::class.java).action()
}

fun ProjectStructureUI.moduleSettings(action: ModuleSettingsSectionUI.() -> Unit) {
  xx("//div[@name='centerSection']", ModuleSettingsSectionUI::class.java).list().firstOrNull()?.action()
  ?: error("Module settings section was not found")

}

fun ProjectStructureUI.facetsSettings(action: FacetsSettingsSectionUI.() -> Unit) {
  xx("//div[@name='centerSection']", FacetsSettingsSectionUI::class.java).list().firstOrNull()?.action()
  ?: error("Facets settings section was not found")
}

fun ProjectStructureUI.artifactsSettings(action: ArtifactsSettingsSectionUI.() -> Unit) {
  xx("//div[@name='centerSection']", ArtifactsSettingsSectionUI::class.java).list().firstOrNull()?.action()
  ?: error("Artifacts settings section was not found")
}

class ProjectSettingsSectionUI(data: ComponentData) : UiComponent(data) {
  fun getProjectName(): String {
    return textField {
      and(byClass("JBTextField"), byAccessibleName("Name:"))
    }.text
  }
}

class ModuleSettingsSectionUI(data: ComponentData) : UiComponent(data) {
  fun getProjectTree(projectName: String, expand: Boolean = false): JTreeUiComponent {
    val tree: JTreeUiComponent = tree("//div[@class='Tree' and @visible_text='$projectName']")
    if (expand) tree.expandAll()
    return tree
  }
}

class FacetsSettingsSectionUI(data: ComponentData) : UiComponent(data) {
  fun getFacetsTree(expand: Boolean = false): JTreeUiComponent {
    val tree: JTreeUiComponent = tree()
    if (expand) tree.expandAll()
    return tree
  }
}

class ArtifactsSettingsSectionUI(data: ComponentData) : UiComponent(data) {
  fun getArtifactsTree(expand: Boolean = false): JTreeUiComponent {
    val tree: JTreeUiComponent = tree("//div[@accessiblename='Add']/parent::*/parent::*/parent::*//div[@class='Tree']")
    if (expand) tree.expandAll()
    return tree
  }
}