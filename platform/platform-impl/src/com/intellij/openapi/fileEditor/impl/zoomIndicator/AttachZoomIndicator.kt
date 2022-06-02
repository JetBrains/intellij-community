// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.zoomIndicator

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.awt.RelativePoint
import javax.swing.JComponent

class AttachZoomIndicator : EditorFactoryListener {
  private fun service(project: Project) = project.service<ZoomIndicatorManager>()
  override fun editorCreated(event: EditorFactoryEvent) {
    val editorEx = event.editor as? EditorImpl ?: return
    val project = editorEx.project ?: return
    if (editorEx.isDisposed || project.isDisposed) return
    val suppressZI = editorEx.getUserData(ZoomIndicatorManager.SUPPRESS_ZOOM_INDICATOR) == true
    if (suppressZI) return
    editorEx.addPropertyChangeListener {
      if (editorEx.isDisposed || !ZoomIndicatorManager.isEnabled) return@addPropertyChangeListener
      if (it.propertyName != EditorEx.PROP_FONT_SIZE) return@addPropertyChangeListener

      invokeLater {
        if (!editorEx.isDisposed) {
          val balloon = service(project).createOrGetBalloon(editorEx)
          balloon?.showInBottomCenterOf(getComponentToUse(project, editorEx))
        }
      }
    }
  }

  override fun editorReleased(event: EditorFactoryEvent) {
    val editor = event.editor
    val project = event.editor.project ?: return
    if (editor.isDisposed || project.isDisposed) return
    if (service(project).editor == editor) {
      service(project).cancelCurrentPopup()
    }
  }

  private fun getComponentToUse(project: Project, editorEx: EditorEx): JComponent {
    return if (!EditorSettingsExternalizable.getInstance().isWheelFontChangePersistent) {
      editorEx.component
    }
    else {
      (FileEditorManager.getInstance(project) as? FileEditorManagerImpl)?.mainSplitters ?: editorEx.component
    }
  }

  private fun Balloon.showInBottomCenterOf(component: JComponent) = show(RelativePoint.getSouthOf(component), Balloon.Position.below)
}