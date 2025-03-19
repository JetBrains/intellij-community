// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection.suppression

import com.intellij.codeInsight.daemon.impl.TrafficLightRenderer
import com.intellij.codeInsight.daemon.impl.TrafficLightRendererContributor
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.AnalyzerStatus
import com.intellij.openapi.editor.markup.AnalyzingType
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import org.jetbrains.plugins.gradle.config.isGradleFile
import org.jetbrains.plugins.gradle.service.resolve.getLinkedGradleProjectPath

private class GradleGroovyTrafficLightRendererContributor : TrafficLightRendererContributor {
  override fun createRenderer(editor: Editor, file: PsiFile?): TrafficLightRenderer? {
    if (file == null || !file.isGradleFile()) {
      return null
    }
    return GradleGroovyTrafficLightRenderer(file, editor)
  }
}

private class GradleGroovyTrafficLightRenderer(private val file: PsiFile, editor: Editor) : TrafficLightRenderer(file.project, editor) {
  override fun getStatus(): AnalyzerStatus {
    val thisLinkedProjectPath = file.getLinkedGradleProjectPath() ?: return super.getStatus()
    val service = project.service<GradleSuspendTypecheckingService>()
    if (!service.isSuspended(thisLinkedProjectPath)) {
      return super.getStatus()
    }
    return AnalyzerStatus(
      icon = AllIcons.RunConfigurations.TestIgnored,
      title = GradleInspectionBundle.message("traffic.light.inspections.disabled"),
      details = GradleInspectionBundle.message("traffic.light.inspections.disabled.description"),
      controller = uiController,
    )
      .withAnalyzingType(AnalyzingType.PARTIAL)
  }
}