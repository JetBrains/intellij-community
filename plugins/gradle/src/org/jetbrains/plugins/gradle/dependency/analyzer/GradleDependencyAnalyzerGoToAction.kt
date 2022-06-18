// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dependency.analyzer

import com.intellij.buildsystem.model.DeclaredDependency
import com.intellij.buildsystem.model.unified.UnifiedCoordinates
import com.intellij.externalSystem.DependencyModifierService
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerView
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.dependency.analyzer.GradleDependencyAnalyzerContributor.Companion.MODULE_DATA
import org.jetbrains.plugins.gradle.util.GradleConstants
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency as Dependency

class GradleDependencyAnalyzerGoToAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val dependency = getDeclaredDependency(e) ?: return
    val psiElement = dependency.psiElement ?: return
    val navigationSupport = PsiNavigationSupport.getInstance()
    val navigatable = navigationSupport.getDescriptor(psiElement) ?: return
    if (navigatable.canNavigate()) {
      navigatable.navigate(true)
    }
  }

  override fun update(e: AnActionEvent) {
    val systemId = e.getData(ExternalSystemDataKeys.EXTERNAL_SYSTEM_ID)
    e.presentation.isEnabledAndVisible =
      systemId == GradleConstants.SYSTEM_ID &&
      getDeclaredDependency(e) != null
  }

  private fun getDeclaredDependency(e: AnActionEvent): DeclaredDependency? {
    val project = e.project ?: return null
    val dependency = e.getData(DependencyAnalyzerView.DEPENDENCY) ?: return null
    val coordinates = getUnifiedCoordinates(dependency) ?: return null
    val module = getParentModule(project, dependency) ?: return null
    val dependencyModifierService = DependencyModifierService.getInstance(project)
    return dependencyModifierService.declaredDependencies(module)
      .find { it.coordinates == coordinates }
  }

  private fun getUnifiedCoordinates(dependency: Dependency): UnifiedCoordinates? {
    return when (val data = dependency.data) {
      is Dependency.Data.Artifact -> getUnifiedCoordinates(data)
      is Dependency.Data.Module -> getUnifiedCoordinates(data)
    }
  }

  private fun getUnifiedCoordinates(data: Dependency.Data.Artifact): UnifiedCoordinates {
    return UnifiedCoordinates(data.groupId, data.artifactId, data.version)
  }

  private fun getUnifiedCoordinates(data: Dependency.Data.Module): UnifiedCoordinates? {
    val moduleData = data.getUserData(MODULE_DATA) ?: return null
    return UnifiedCoordinates(moduleData.group, moduleData.externalName, moduleData.version)
  }

  private fun getParentModule(project: Project, dependency: Dependency): Module? {
    val parentData = dependency.parent?.data as? Dependency.Data.Module ?: return null
    return getModule(project, parentData)
  }

  private fun getModule(project: Project, data: Dependency.Data.Module): Module? {
    val moduleData = data.getUserData(MODULE_DATA) ?: return null
    val moduleManager = ModuleManager.getInstance(project)
    return moduleManager.findModuleByName(moduleData.ideGrouping)
  }

  init {
    templatePresentation.icon = null
    templatePresentation.text = ExternalSystemBundle.message("external.system.dependency.analyzer.go.to", "build.gradle")
  }
}