// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.util.scenarios

import com.intellij.testGuiFramework.fixtures.JDialogFixture
import com.intellij.testGuiFramework.impl.*
import com.intellij.testGuiFramework.util.scenarios.ProjectStructureDialogModel.Constants.buttonCancel
import com.intellij.testGuiFramework.util.scenarios.ProjectStructureDialogModel.Constants.itemLibrary
import com.intellij.testGuiFramework.util.scenarios.ProjectStructureDialogModel.Constants.menuArtifacts
import com.intellij.testGuiFramework.util.scenarios.ProjectStructureDialogModel.Constants.menuFacets
import com.intellij.testGuiFramework.util.scenarios.ProjectStructureDialogModel.Constants.menuLibraries
import com.intellij.testGuiFramework.util.scenarios.ProjectStructureDialogModel.Constants.menuModules
import com.intellij.testGuiFramework.util.scenarios.ProjectStructureDialogModel.Constants.menuSDKs
import com.intellij.testGuiFramework.util.scenarios.ProjectStructureDialogModel.Constants.projectStructureTitle
import com.intellij.testGuiFramework.util.step
import com.intellij.testGuiFramework.utils.TestUtilsClass
import com.intellij.testGuiFramework.utils.TestUtilsClassCompanion

class ProjectStructureDialogModel(val testCase: GuiTestCase) : TestUtilsClass(testCase) {
  companion object : TestUtilsClassCompanion<ProjectStructureDialogModel>(
    { ProjectStructureDialogModel(it) }
  )
  object Constants{
    const val projectStructureTitle = "Project Structure"

    const val menuProject = "Project"
    const val menuModules = "Modules"
    const val menuLibraries = "Libraries"
    const val itemLibrary = "Library"
    const val menuFacets = "Facets"
    const val itemFacet = "Facet"
    const val menuArtifacts = "Artifacts"
    const val itemArtifact = "Artifact"
    const val menuSDKs = "SDKs"
    const val itemSDK = "SDK"
    const val menuGlobalLibraries = "Global Libraries"
    const val menuProblems = "Problems"

    const val buttonCancel = "Cancel"
  }
}

val GuiTestCase.projectStructureDialogModel by ProjectStructureDialogModel

fun ProjectStructureDialogModel.connectDialog(): JDialogFixture =
  testCase.dialog(projectStructureTitle, true)

fun ProjectStructureDialogModel.checkInProjectStructure(actions: GuiTestCase.()->Unit){
  with(guiTestCase){
    val dialog = connectDialog()
    try {
      this.actions()
    }
    finally {
      step("close '$projectStructureTitle' dialog with Cancel") {
        dialog.button(buttonCancel).click()
      }
    }
  }
}

fun ProjectStructureDialogModel.checkLibraryPresent(vararg library: String){
  checkLibrary {
    guiTestCase.testTreeItemExist(itemLibrary, *library)
    jTree(*library).clickPath()
  }
}

private fun ProjectStructureDialogModel.checkPage(page: String, checks: JDialogFixture.()->Unit){
  with(guiTestCase){
    step("at '$page' page in Project Structure dialog") {
      val dialog = connectDialog()
      dialog.jList(page).clickItem(page)
      dialog.checks()
    }
  }
}

fun ProjectStructureDialogModel.checkModule(checks: JDialogFixture.()->Unit){
  checkPage(menuModules, checks)
}

fun ProjectStructureDialogModel.checkArtifact(checks: JDialogFixture.()->Unit){
  checkPage(menuArtifacts, checks)
}

fun ProjectStructureDialogModel.checkSDK(checks: JDialogFixture.()->Unit){
  checkPage(menuSDKs, checks)
}

fun ProjectStructureDialogModel.checkFacet(checks: JDialogFixture.()->Unit){
  checkPage(menuFacets, checks)
}

fun ProjectStructureDialogModel.checkLibrary(checks: JDialogFixture.()->Unit){
  checkPage(menuLibraries, checks)
}
