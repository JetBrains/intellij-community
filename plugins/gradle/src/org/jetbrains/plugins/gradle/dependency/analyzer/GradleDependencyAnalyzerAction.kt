// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.dependency.analyzer

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.dependency.analyzer.AbstractDependencyAnalyzerAction
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerView
import org.jetbrains.plugins.gradle.util.GradleConstants

class GradleDependencyAnalyzerAction : AbstractDependencyAnalyzerAction() {
  override fun getSystemId(e: AnActionEvent) = GradleConstants.SYSTEM_ID

  override fun setSelectedState(e: AnActionEvent, view: DependencyAnalyzerView) {}
}