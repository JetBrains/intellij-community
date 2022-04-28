// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.zoomIndicator

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.BalloonImpl
import com.intellij.util.Alarm
import com.intellij.util.EventDispatcher
import java.util.*

private const val POPUP_TIMEOUT_MS = 5000

@Service(Service.Level.PROJECT)
class ZoomIndicatorManager(project: Project) {
  companion object {
    val isEnabled: Boolean
      get() = AdvancedSettings.getBoolean("editor.show.zoom.indicator")
  }
  private interface Listener : EventListener { fun onUpdate(value: Int) }
  private val eventDispatcher = EventDispatcher.create(Listener::class.java)
  private val alarm = Alarm(project)
  private val popupActions = arrayListOf("ResetFontSizeAction", "com.intellij.openapi.fileEditor.impl.zoomIndicator.ZoomIndicatorView\$settingsAction\$1")

  var balloon: Balloon? = null
  var editor: EditorEx? = null
  var fontSize = 0
    set(value) {
      field = value
      eventDispatcher.multicaster.onUpdate(value)
    }

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

  fun createOrGetBalloon(editorEx: EditorEx): Balloon? {
    val view = ZoomIndicatorView(this, editorEx)
    val b = balloon
    if (editorEx == editor && (b as? BalloonImpl)?.isVisible == true) return null
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
    fontSize = 0
  }

  fun adviseFontSize(disposable: Disposable, handler: (Int) -> Unit) {
    handler(fontSize)
    eventDispatcher.addListener(object : Listener {
      override fun onUpdate(value: Int) {
        if (value == 0) return
        handler(fontSize)
      }
    }, disposable)
  }

  private fun Balloon.getView() = ((this as BalloonImpl).content as ZoomIndicatorView)
}