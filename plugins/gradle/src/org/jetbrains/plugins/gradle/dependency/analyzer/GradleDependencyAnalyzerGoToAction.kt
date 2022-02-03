// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dependency.analyzer

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerView
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import org.jetbrains.plugins.gradle.util.GradleConstants

class GradleDependencyAnalyzerGoToAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    logger<GradleDependencyAnalyzerGoToAction>().error("TODO: GradleDependencyAnalyzerGoToAction")
  }

  override fun update(e: AnActionEvent) {
    val systemId = e.getData(DependencyAnalyzerView.EXTERNAL_SYSTEM_ID)
    val dependency = e.getData(DependencyAnalyzerView.DEPENDENCY)
    e.presentation.isEnabledAndVisible =
      systemId == GradleConstants.SYSTEM_ID &&
      dependency?.data is DependencyAnalyzerDependency.Data.Artifact
  }

  init {
    templatePresentation.icon = null
    templatePresentation.text = ExternalSystemBundle.message("external.system.dependency.analyzer.go.to", "build.gradle")
  }
}