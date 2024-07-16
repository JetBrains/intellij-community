// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.inspector

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.markup.AnalyzerStatus
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint

interface InspectionsSettingContentService {
  companion object {
    fun getInstance(): InspectionsSettingContentService = ApplicationManager.getApplication().getService(InspectionsSettingContentService::class.java)
  }
  fun showPopup(analyzerGetter: () -> AnalyzerStatus, project: Project, point: RelativePoint, fusTabId: Int)
}

class InspectionsSettingContentServiceImpl : InspectionsSettingContentService {
  override fun showPopup(analyzerGetter: () -> AnalyzerStatus, project: Project, point: RelativePoint, fusTabId: Int) {
    val panel = InspectionsSettingContent(analyzerGetter, project, fusTabId).panel

    JBPopupFactory.getInstance()
      .createComponentPopupBuilder(panel, panel)
      .setRequestFocus(true).createPopup().show(point)
  }
}