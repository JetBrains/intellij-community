// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.zoomIndicator

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.editor.Editor
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

private class AttachZoomIndicator : EditorFactoryListener {
  private fun service(project: Project) = project.service<ZoomIndicatorManager>()

  private fun shouldSuppressZoomIndicator(editor: Editor): Boolean {
    if (editor.isDisposed) return true
    if (editor.getUserData(ZoomIndicatorManager.SUPPRESS_ZOOM_INDICATOR) == true) return true
    if (editor.getUserData(ZoomIndicatorManager.SUPPRESS_ZOOM_INDICATOR_ONCE) == true) {
      editor.putUserData(ZoomIndicatorManager.SUPPRESS_ZOOM_INDICATOR_ONCE, false)
      return true
    }
    return false
  }

  override fun editorCreated(event: EditorFactoryEvent) {
    val editorEx = event.editor as? EditorImpl ?: return
    val project = editorEx.project ?: return
    if (project.isDisposed || shouldSuppressZoomIndicator(editorEx)) return
    editorEx.addPropertyChangeListener {
      if (it.propertyName != EditorEx.PROP_FONT_SIZE) return@addPropertyChangeListener
      if (!ZoomIndicatorManager.isEditorZoomIndicatorEnabled || shouldSuppressZoomIndicator(editorEx)) return@addPropertyChangeListener

      invokeLater {
        if (!editorEx.isDisposed && editorEx.component.isShowing) {
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
    val manager = project.serviceIfCreated<ZoomIndicatorManager>()
    if (manager?.editor == editor) {
      manager.cancelCurrentPopup()
    }
  }

  private fun getComponentToUse(project: Project, editorEx: EditorEx): JComponent {
    return if (EditorSettingsExternalizable.getInstance().isWheelFontChangePersistent) {
      (FileEditorManager.getInstance(project) as? FileEditorManagerImpl)?.component ?: editorEx.component
    }
    else {
      editorEx.component
    }
  }

  private fun Balloon.showInBottomCenterOf(component: JComponent) = show(RelativePoint.getSouthOf(component), Balloon.Position.below)
}