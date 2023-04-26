// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl.ui

import com.intellij.ide.IdeBundle
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationDisplayType.*
import com.intellij.notification.impl.NotificationsConfigurationImpl
import com.intellij.notification.impl.NotificationsManagerImpl
import com.intellij.notification.impl.isSoundEnabled
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.util.ui.JBUI
import javax.swing.DefaultComboBoxModel
import javax.swing.JCheckBox

/**
 * @author Konstantin Bulenkov
 */
class NotificationSettingsUi(var notification: NotificationSettingsWrapper, private val useBalloonNotifications: ComponentPredicate) {
  val ui: DialogPanel
  private lateinit var type: ComboBox<NotificationDisplayType>
  private lateinit var log: JCheckBox
  private lateinit var playSound: JCheckBox
  private lateinit var readAloud: JCheckBox
  init {
    val model = createComboboxModel(notification)
    ui = panel {
      row(IdeBundle.message("notifications.configurable.column.popup")) {
        type = comboBox(model,
                        renderer = SimpleListCellRenderer.create("") { if(useBalloonNotifications.invoke()) it?.title else NONE.title})
          .bindItem(notification::displayType.toNullableProperty(NONE))
          .enabledIf(useBalloonNotifications)
          .component
        type.addActionListener {
          notification.displayType = type.selectedItem as NotificationDisplayType
        }
      }
      row {
        log = checkBox(IdeBundle.message("notifications.configurable.column.toolwindow"))
          .bindSelected(notification::isShouldLog)
          .component
        log.addActionListener {
          notification.isShouldLog = log.isSelected
        }
      }
      if (isSoundEnabled()) {
        row {
          playSound = checkBox(IdeBundle.message("notifications.configurable.play.sound"))
            .bindSelected(notification::isPlaySound)
            .component
          playSound.addActionListener {
            notification.isPlaySound = playSound.isSelected
          }
        }
      }
      if (isReadAloudEnabled()) {
        row {
          readAloud = checkBox(IdeBundle.message("notifications.configurable.column.read.aloud"))
            .bindSelected(notification::isShouldReadAloud)
            .component
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
      readAloud.isSelected = notification.isShouldReadAloud && !NotificationsManagerImpl.isAnnouncingEnabled()
      readAloud.isEnabled = !NotificationsManagerImpl.isAnnouncingEnabled()
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

private fun isReadAloudEnabled() = SystemInfo.isMac