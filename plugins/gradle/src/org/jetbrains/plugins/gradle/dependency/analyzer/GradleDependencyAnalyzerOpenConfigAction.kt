// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dependency.analyzer

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerView
import com.intellij.openapi.externalSystem.dependency.analyzer.ExternalSystemDependencyAnalyzerOpenConfigAction
import org.jetbrains.plugins.gradle.dependency.analyzer.GradleDependencyAnalyzerContributor.Companion.MODULE_DATA
import org.jetbrains.plugins.gradle.util.GradleConstants
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency as Dependency

class GradleDependencyAnalyzerOpenConfigAction : ExternalSystemDependencyAnalyzerOpenConfigAction(GradleConstants.SYSTEM_ID) {

  override fun getExternalProjectPath(e: AnActionEvent): String? {
    val dependency = e.getData(DependencyAnalyzerView.DEPENDENCY) ?: return null
    val dependencyData = dependency.data as? Dependency.Data.Module ?: return null
    val moduleData = dependencyData.getUserData(MODULE_DATA) ?: return null
    return moduleData.linkedExternalProjectPath
  }
}
