// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.util.scenarios

import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.utils.TestUtilsClass
import com.intellij.testGuiFramework.utils.TestUtilsClassCompanion

class NewProjectDialogScenarios(val testCase: GuiTestCase) : TestUtilsClass(testCase) {
  companion object : TestUtilsClassCompanion<NewProjectDialogScenarios>(
    { NewProjectDialogScenarios(it) }
  )
}

val GuiTestCase.newProjectDialogScenarios by NewProjectDialogScenarios

fun NewProjectDialogScenarios.createJavaProjectScenario(projectPath: String, libs: LibrariesSet = emptySet(), template: String = "", basePackage: String = "") {
  with(testCase) {
    assertProjectPathExists(projectPath)
    welcomePageDialogModel.createNewProject()
    newProjectDialogModel.createJavaProject(projectPath, libs, template, basePackage)
  }
}

fun NewProjectDialogScenarios.createJavaEnterpriseProjectScenario(projectPath: String, libs: LibrariesSet = emptySet(), template: String = "") {
  with(testCase) {
    assertProjectPathExists(projectPath)
    welcomePageDialogModel.createNewProject()
    newProjectDialogModel.createJavaEnterpriseProject(projectPath, libs, template)
  }
}

fun NewProjectDialogScenarios.createJBossProjectScenario(projectPath: String, libs: LibrariesSet = emptySet()) {
  with(testCase) {
    assertProjectPathExists(projectPath)
    welcomePageDialogModel.createNewProject()
    newProjectDialogModel.createJBossProject(projectPath, libs)
  }
}

/**
 * Creates a new project from a specified group (ultimate only)
 * Supported only simple groups with 2 pages - first with framework selection and last with specifying project location
 * @param group - group where project is expected to be created. Not all groups are supported
 * @param projectPath - path where the project is going to be created
 * @param libs - path to additional library/framework that should be checked
 * Note: only one library/framework can be checked!
 * */
fun NewProjectDialogScenarios.createProjectInGroupScenario(group: NewProjectDialogModel.Groups, projectPath: String, libs: LibrariesSet = emptySet()) {
  with(testCase) {
    assertProjectPathExists(projectPath)
    welcomePageDialogModel.createNewProject()
    newProjectDialogModel.createProjectInGroup(group, projectPath, libs)
  }
}

fun NewProjectDialogScenarios.createGradleProjectScenario(projectPath: String, gradleOptions: NewProjectDialogModel.GradleProjectOptions){
  with(testCase) {
    assertProjectPathExists(projectPath)
    welcomePageDialogModel.createNewProject()
    newProjectDialogModel.createGradleProject(projectPath, gradleOptions)
  }
}

fun NewProjectDialogScenarios.createMavenProjectScenario(projectPath: String, mavenOptions: NewProjectDialogModel.MavenProjectOptions){
  with(testCase) {
    assertProjectPathExists(projectPath)
    welcomePageDialogModel.createNewProject()
    newProjectDialogModel.createMavenProject(projectPath, mavenOptions)
  }
}
