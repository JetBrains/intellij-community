// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.presentationAssistant

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.util.addMouseListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.platform.ide.impl.presentationAssistant.ActionInfoPopupGroup.Companion.setBorderColorIfNeeded
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.Alarm
import com.intellij.util.IconUtil
import kotlinx.coroutines.CoroutineScope
import java.awt.Dimension
import java.awt.Point
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import javax.swing.SwingConstants

internal class PresentationAssistantQuickSettingsButton(private val project: Project,
                                                        private val appearance: ActionInfoPopupGroup.Appearance,
                                                        private val shownStateRequestCountChanged: (Int) -> Unit):
  JBLabel(IconUtil.colorize(AllIcons.Actions.PresentationAssistantSettings, appearance.theme.keymapLabel)), Disposable {

  @Service(Service.Level.PROJECT)
  private class AlarmFactory (private val coroutineScope: CoroutineScope) {
    fun createAlarm() = Alarm(coroutineScope)
  }

  private var popup: JBPopup? = null
  private var hideAlarm = project.service<AlarmFactory>().createAlarm()
  private var shownStateRequestCount = 0
    set(value) {
      val oldValue = field
      field = value

      if (oldValue != shownStateRequestCount) {
        if (shownStateRequestCount > 0) cancelHideAlarm()
        else scheduleHide()
      }
      shownStateRequestCountChanged(shownStateRequestCount)
    }

  init {
    background = appearance.theme.background
    isOpaque = true
    updatePreferredSize()

    addMouseListener(this, object : MouseListener {
      override fun mouseClicked(e: MouseEvent?) {}

      override fun mousePressed(e: MouseEvent?) {}

      override fun mouseReleased(e: MouseEvent?) {
        showSettingsPopup()
      }

      override fun mouseEntered(e: MouseEvent?) {
        shownStateRequestCount++
      }

      override fun mouseExited(e: MouseEvent?) {
        releaseShownStateRequest()
      }
    })
  }

  fun updatePreferredSize() {
    val width = JBUIScale.scale(appearance.settingsButtonWidth)
    preferredSize = Dimension(width, width)
  }

  private fun showSettingsPopup() {
    shownStateRequestCount++
    val popup = JBPopupFactory.getInstance().createActionGroupPopup("",
                                                                    PresentationAssistantQuickSettingsGroup(),
                                                                    SimpleDataContext.getSimpleContext(CommonDataKeys.PROJECT, project, null),
                                                                    null,
                                                                    false,
                                                                    { releaseShownStateRequest() },
                                                                    Int.MAX_VALUE)
    popup.isShowSubmenuOnHover = true
    popup.setAdText(IdeBundle.message("presentation.assistant.quick.settings.ad").asHtml, SwingConstants.LEFT)

    popup.showInBestPositionFor(SimpleDataContext
                                  .builder()
                                  .add(PlatformDataKeys.CONTEXT_COMPONENT, this)
                                  .add(PlatformDataKeys.CONTEXT_MENU_POINT, Point(0, height + appearance.spaceBetweenPopups))
                                  .build())
  }

  private val String.asHtml: String get() = "<html>" + replace("\n", "<br>") + "</html>"

  fun acquireShownStateRequest(point: RelativePoint) {
    shownStateRequestCount++
    if (shownStateRequestCount > 0 && popup == null) showPopup(point)
  }

  private fun showPopup(point: RelativePoint) {
    hidePopup()
    popup = createSettingsButtonPopup()
    popup?.show(point)
  }

  fun releaseShownStateRequest() {
    if (shownStateRequestCount > 0) shownStateRequestCount--
  }

  private fun scheduleHide() {
    cancelHideAlarm()
    hideAlarm.addRequest({
      hidePopup()
    }, 2000)
  }

  private fun cancelHideAlarm() {
    hideAlarm.cancelAllRequests()
  }

  fun hidePopup() {
    if (popup == null) return

    popup?.cancel()
    popup = null
    cancelHideAlarm()
    shownStateRequestCount = 0
  }

  override fun dispose() {
    hidePopup()
  }

  private fun createSettingsButtonPopup(): JBPopup {
    val popup = with(JBPopupFactory.getInstance().createComponentPopupBuilder(this, this)) {
      setBorderColorIfNeeded(appearance.theme)
      setFocusable(true)
      setBelongsToGlobalPopupStack(false)
      setCancelKeyEnabled(false)
      createPopup()
    }

    return popup
  }
}