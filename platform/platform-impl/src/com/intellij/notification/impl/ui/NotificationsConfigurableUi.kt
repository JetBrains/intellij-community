// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl.ui

import com.intellij.ide.IdeBundle
import com.intellij.notification.NotificationGroup
import com.intellij.notification.impl.NotificationsConfigurationImpl
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.ui.ListSpeedSearch
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.selected
import org.jetbrains.annotations.Nullable
import javax.swing.JCheckBox
import javax.swing.ListSelectionModel

/**
 * @author Konstantin Bulenkov
 */
class NotificationsConfigurableUi(settings: NotificationsConfigurationImpl) : ConfigurableUi<NotificationsConfigurationImpl> {
  private val ui: DialogPanel
  private val notificationsList = createNotificationsList()
  private val speedSearch = object : ListSpeedSearch<NotificationSettingsWrapper>(notificationsList, null, null) {
    override fun isMatchingElement(element: Any?, pattern: String?): Boolean {
      if (super.isMatchingElement(element, pattern)) {
        return true
      }
      if (element != null && pattern != null) {
        return super.isMatchingElement(element, NotificationGroup.getGroupTitle(pattern))
      }
      return false
    }
  }
  private lateinit var useBalloonNotifications: JCheckBox
  private lateinit var useSystemNotifications: JCheckBox
  private lateinit var notificationSettings: NotificationSettingsUi
  private val myDoNotAskConfigurableUi = DoNotAskConfigurableUi()

  init {
    speedSearch.setupListeners()
    ui = panel {
      row {
        useBalloonNotifications = checkBox(IdeBundle.message("notifications.configurable.display.balloon.notifications"))
          .bindSelected(settings::SHOW_BALLOONS)
          .component
      }
      row {
        useSystemNotifications = checkBox(IdeBundle.message("notifications.configurable.enable.system.notifications"))
          .bindSelected(settings::SYSTEM_NOTIFICATIONS)
          .component
      }
      row {
        notificationSettings = NotificationSettingsUi(notificationsList.model.getElementAt(0), useBalloonNotifications.selected)
        scrollCell(notificationsList)
        cell(notificationSettings.ui)
          .align(AlignY.TOP)
      }
      row {
        cell(myDoNotAskConfigurableUi.createComponent())
          .label(IdeBundle.message("notifications.configurable.do.not.ask.title"), LabelPosition.TOP)
          .align(Align.FILL)
      }.topGap(TopGap.SMALL)
        .resizableRow()
    }
    ScrollingUtil.ensureSelectionExists(notificationsList)
  }

  private fun createNotificationsList(): JBList<NotificationSettingsWrapper> {
    return JBList(*NotificationsConfigurablePanel.NotificationsTreeTableModel().allSettings
      .sortedWith(Comparator { nsw1, nsw2 -> NaturalComparator.INSTANCE.compare(nsw1.toString(), nsw2.toString()) })
      .toTypedArray())
      .apply {
        cellRenderer = SimpleListCellRenderer.create("") { it.toString() }
        selectionModel.addListSelectionListener {
          selectedValue?.let { notificationSettings.updateUi(it) }
        }
        selectionMode = ListSelectionModel.SINGLE_SELECTION
      }
  }

  override fun reset(settings: NotificationsConfigurationImpl) {
    ui.reset()
    val selectedIndex = notificationsList.selectedIndex
    notificationsList.model = createNotificationsList().model
    notificationsList.selectedIndex = selectedIndex
    notificationSettings.updateUi(notificationsList.selectedValue)
    myDoNotAskConfigurableUi.reset()
  }

  override fun isModified(settings: NotificationsConfigurationImpl): Boolean {
    return ui.isModified() || isNotificationsModified() || myDoNotAskConfigurableUi.isModified()
  }

  private fun isNotificationsModified(): Boolean {
    for (i in 0 until notificationsList.model.size) {
      if (notificationsList.model.getElementAt(i).hasChanged()) {
        return true
      }
    }
    return false
  }

  @Nullable
  override fun enableSearch(option: String?): Runnable? {
    if (option == null) return null
    return Runnable {speedSearch.findAndSelectElement(option) }
  }

  override fun apply(settings: NotificationsConfigurationImpl) {
    ui.apply()
    for (i in 0 until notificationsList.model.size) {
      val settingsWrapper = notificationsList.model.getElementAt(i)
      if (settingsWrapper.hasChanged()) {
        settingsWrapper.apply()
      }
    }
    myDoNotAskConfigurableUi.apply()
  }

  override fun getComponent() = ui
}
