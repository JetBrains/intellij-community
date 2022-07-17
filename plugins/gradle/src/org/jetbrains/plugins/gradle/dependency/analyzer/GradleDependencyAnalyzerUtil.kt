// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dependency.analyzer

import com.intellij.buildsystem.model.unified.UnifiedCoordinates
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.util.GradleUtil


fun getUnifiedCoordinates(dependency: DependencyAnalyzerDependency): UnifiedCoordinates? {
  return when (val data = dependency.data) {
    is DependencyAnalyzerDependency.Data.Artifact -> getUnifiedCoordinates(data)
    is DependencyAnalyzerDependency.Data.Module -> getUnifiedCoordinates(data)
  }
}

fun getUnifiedCoordinates(data: DependencyAnalyzerDependency.Data.Artifact): UnifiedCoordinates {
  return UnifiedCoordinates(data.groupId, data.artifactId, data.version)
}

fun getUnifiedCoordinates(data: DependencyAnalyzerDependency.Data.Module): UnifiedCoordinates? {
  val moduleData = data.getUserData(GradleDependencyAnalyzerContributor.MODULE_DATA) ?: return null
  return UnifiedCoordinates(moduleData.group, moduleData.externalName, moduleData.version)
}

fun getParentModule(project: Project, dependency: DependencyAnalyzerDependency): Module? {
  val parentData = dependency.parent?.data as? DependencyAnalyzerDependency.Data.Module ?: return null
  return getModule(project, parentData)
}

fun getModule(project: Project, data: DependencyAnalyzerDependency.Data.Module): Module? {
  val moduleData = data.getUserData(GradleDependencyAnalyzerContributor.MODULE_DATA) ?: return null
  return GradleUtil.findGradleModule(project, moduleData)
}
