// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.zoomIndicator

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Key
import com.intellij.ui.BalloonImpl
import com.intellij.util.Alarm

private const val POPUP_TIMEOUT_MS = 5000

@Service(Service.Level.PROJECT)
class ZoomIndicatorManager(project: Project) {
  companion object {
    @JvmField
    val SUPPRESS_ZOOM_INDICATOR = Key.create<Boolean>("SUPPRESS_ZOOM_INDICATOR")
    @JvmField
    val SUPPRESS_ZOOM_INDICATOR_ONCE = Key.create<Boolean>("SUPPRESS_ZOOM_INDICATOR_ONCE")
    val isEnabled: Boolean
      get() = AdvancedSettings.getBoolean("editor.show.zoom.indicator")
  }
  private val alarm = Alarm(project)
  private val popupActions = arrayListOf("ResetFontSizeAction", "com.intellij.openapi.fileEditor.impl.zoomIndicator.ZoomIndicatorView\$settingsAction\$1")

  var balloon: Balloon? = null
  var editor: EditorEx? = null

  init {
    project.messageBus.connect().subscribe(AnActionListener.TOPIC, object : AnActionListener {
      override fun afterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult) {
        if (event.place == ActionPlaces.POPUP &&
            (popupActions.contains(action::class.java.name) || popupActions.contains(action::class.java.simpleName))) {
          cancelCurrentPopup()
        }
      }
    })
  }

  fun createOrGetBalloon(editorEx: EditorImpl): Balloon? {
    val view = ZoomIndicatorView(editorEx)
    val b = balloon
    if (editorEx == editor && (b as? BalloonImpl)?.isVisible == true) {
      b.getView().updateFontSize()
      return null
    }
    cancelCurrentPopup()
    val b2 = JBPopupFactory.getInstance().createBalloonBuilder(view)
      .setRequestFocus(false)
      .setShadow(true)
      .setFillColor(view.background)
      .setShowCallout(false)
      .setFadeoutTime(0)
      .createBalloon().apply { setAnimationEnabled(false) }
    alarm.addRequest({ cancelBalloonAlarmRequest() }, POPUP_TIMEOUT_MS)
    balloon = b2
    editor = editorEx
    return b2
  }

  private fun cancelBalloonAlarmRequest() {
    alarm.cancelAllRequests()
    val b = balloon ?: return
    if (!b.getView().isHovered) cancelCurrentPopup()
    else alarm.addRequest({ cancelBalloonAlarmRequest() }, POPUP_TIMEOUT_MS)
  }

  fun cancelCurrentPopup() {
    balloon?.hide()
    balloon = null
    editor = null
  }

  private fun Balloon.getView() = ((this as BalloonImpl).content as ZoomIndicatorView)
}