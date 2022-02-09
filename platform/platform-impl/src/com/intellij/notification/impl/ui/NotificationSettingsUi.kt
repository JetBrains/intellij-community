// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl.ui

import com.intellij.ide.IdeBundle
import com.intellij.notification.ActionCenter
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationDisplayType.*
import com.intellij.notification.impl.NotificationsConfigurationImpl
import com.intellij.notification.impl.isReadAloudEnabled
import com.intellij.notification.impl.isSoundEnabled
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBUI
import javax.swing.DefaultComboBoxModel
import javax.swing.JCheckBox

/**
 * @author Konstantin Bulenkov
 */
class NotificationSettingsUi(var notification: NotificationSettingsWrapper, val useBalloonNotifications: ComponentPredicate) {
  val ui: DialogPanel
  private lateinit var type: ComboBox<NotificationDisplayType>
  private lateinit var log: JCheckBox
  private lateinit var playSound: JCheckBox
  private lateinit var readAloud: JCheckBox
  init {
    val model = createComboboxModel(notification)
    ui = panel(LCFlags.fillX) {
      row(IdeBundle.message("notifications.configurable.column.popup")) {
        type = comboBox(model,
                        getter = notification::displayType,
                        setter = {notification.displayType = it ?: NONE},
                        renderer = SimpleListCellRenderer.create("") { if(useBalloonNotifications.invoke()) it?.title else NONE.title})
          .enableIf(useBalloonNotifications)
          .component
        type.addActionListener {
          notification.displayType = type.selectedItem as NotificationDisplayType
        }
      }
      row {
        log = checkBox(IdeBundle.message(
          if (ActionCenter.isEnabled()) "notifications.configurable.column.toolwindow" else "notifications.configurable.column.log"),
                       {notification.isShouldLog},
                       {notification.isShouldLog = it}).component
        log.addActionListener {
          notification.isShouldLog = log.isSelected
        }
      }
      if (isSoundEnabled()) {
        row {
          playSound = checkBox(IdeBundle.message("notifications.configurable.play.sound"),
                               {notification.isPlaySound},
                               {notification.isPlaySound = it}).component
          playSound.addActionListener {
            notification.isPlaySound = playSound.isSelected
          }
        }
      }
      if (isReadAloudEnabled()) {
        row {
          readAloud = checkBox(IdeBundle.message("notifications.configurable.column.read.aloud"),
                               {notification.isShouldReadAloud},
                               {notification.isShouldReadAloud = it}).component
          readAloud.addActionListener {
            notification.isShouldReadAloud = readAloud.isSelected
          }
        }
      }
    }.withBorder(JBUI.Borders.empty(2))
  }

  fun updateUi(notification: NotificationSettingsWrapper) {
    this.notification = notification
    type.model = createComboboxModel(notification)
    type.model.selectedItem = notification.displayType
    log.isSelected = notification.isShouldLog
    if (isReadAloudEnabled()) {
      readAloud.isSelected = notification.isShouldReadAloud
    }
    if (isSoundEnabled()) {
      playSound.isSelected = notification.isPlaySound
    }
  }

  private fun createComboboxModel(notification: NotificationSettingsWrapper):DefaultComboBoxModel<NotificationDisplayType> {
    val hasToolWindowCapability = NotificationsConfigurationImpl.getInstanceImpl().hasToolWindowCapability(notification.groupId)
    val items = if (hasToolWindowCapability) arrayOf(NONE, BALLOON, STICKY_BALLOON, TOOL_WINDOW)
                else arrayOf(NONE, BALLOON, STICKY_BALLOON)

    return DefaultComboBoxModel(items)
  }
}