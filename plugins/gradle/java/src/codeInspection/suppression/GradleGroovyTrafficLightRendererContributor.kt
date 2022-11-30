// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

class GradleGroovyTrafficLightRendererContributor : TrafficLightRendererContributor {
  override fun createRenderer(editor: Editor, file: PsiFile?): TrafficLightRenderer? {
    if (file == null || !file.isGradleFile()) {
      return null
    }
    return GradleGroovyTrafficLightRenderer(file, editor)
  }

}

private class GradleGroovyTrafficLightRenderer(val file: PsiFile, editor: Editor) : TrafficLightRenderer(file.project, editor) {
  override fun getStatus(): AnalyzerStatus {
    val linkedProjectPath = file.getLinkedGradleProjectPath() ?: return super.getStatus()
    val service = project.service<GradleSuspendTypecheckingService>()
    if (!service.isSuspended(linkedProjectPath)) return super.getStatus()
    return AnalyzerStatus(AllIcons.RunConfigurations.TestIgnored, GradleInspectionBundle.message("traffic.light.inspections.disabled"), GradleInspectionBundle.message("traffic.light.inspections.disabled.description"), uiController)
    .withAnalyzingType(AnalyzingType.PARTIAL)
  }
}