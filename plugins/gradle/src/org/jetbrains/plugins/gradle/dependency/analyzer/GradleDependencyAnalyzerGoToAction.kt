// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dependency.analyzer

import com.intellij.buildsystem.model.DeclaredDependency
import com.intellij.externalSystem.DependencyModifierService
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerGoToAction
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerView
import com.intellij.pom.Navigatable
import org.jetbrains.plugins.gradle.util.GradleConstants

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
}