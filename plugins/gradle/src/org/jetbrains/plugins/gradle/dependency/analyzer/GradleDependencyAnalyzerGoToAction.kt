// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dependency.analyzer

import com.intellij.buildsystem.model.DeclaredDependency
import com.intellij.buildsystem.model.unified.UnifiedCoordinates
import com.intellij.externalSystem.DependencyModifierService
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerGoToAction
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerView
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import org.jetbrains.plugins.gradle.dependency.analyzer.GradleDependencyAnalyzerContributor.Companion.MODULE_DATA
import org.jetbrains.plugins.gradle.util.GradleConstants
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency as Dependency

class GradleDependencyAnalyzerGoToAction : DependencyAnalyzerGoToAction(GradleConstants.SYSTEM_ID) {

  override fun getNavigatable(e: AnActionEvent): Navigatable? {
    val dependency = getDeclaredDependency(e) ?: return null
    val psiElement = dependency.psiElement ?: return null
    val navigationSupport = PsiNavigationSupport.getInstance()
    return navigationSupport.getDescriptor(psiElement)
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
}