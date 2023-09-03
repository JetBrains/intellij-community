// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl.ui

import com.intellij.ide.GeneralSettings
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.UISettingsListener
import com.intellij.notification.NotificationAnnouncingMode
import com.intellij.notification.NotificationGroup
import com.intellij.notification.impl.NotificationsConfigurationImpl
import com.intellij.notification.impl.isNotificationAnnouncerFeatureAvailable
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.ui.ListSpeedSearch
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.layout.selected
import org.jetbrains.annotations.Nullable
import javax.swing.JCheckBox
import javax.swing.ListSelectionModel


/**
 * @author Konstantin Bulenkov
 */
internal class NotificationsConfigurableUi(settings: NotificationsConfigurationImpl) : ConfigurableUi<NotificationsConfigurationImpl>, Disposable {
  private val ui: DialogPanel
  private val notificationList = createNotificationList()
  private val speedSearch = object : ListSpeedSearch<NotificationSettingsWrapper>(notificationList, null, null) {
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

  private val screenReaderEnabledProperty = AtomicBooleanProperty(GeneralSettings.getInstance().isSupportScreenReaders)
  private val notificationModeToUserString: Map<NotificationAnnouncingMode, String> =
    if (SystemInfo.isMac) mapOf(
      NotificationAnnouncingMode.NONE to IdeBundle.message("notifications.configurable.announcing.value.off"),
      NotificationAnnouncingMode.MEDIUM to IdeBundle.message("notifications.configurable.announcing.value.medium"),
      NotificationAnnouncingMode.HIGH to IdeBundle.message("notifications.configurable.announcing.value.high")
    )
    else mapOf(
      NotificationAnnouncingMode.NONE to IdeBundle.message("notifications.configurable.announcing.value.off"),
      NotificationAnnouncingMode.MEDIUM to IdeBundle.message("notifications.configurable.announcing.value.not.interrupting"),
      NotificationAnnouncingMode.HIGH to IdeBundle.message("notifications.configurable.announcing.value.interrupting")
    )

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
      if (isNotificationAnnouncerFeatureAvailable) {
        row(IdeBundle.message("notifications.configurable.announcing.title")) {
          val options = listOf(NotificationAnnouncingMode.NONE,
                               NotificationAnnouncingMode.MEDIUM,
                               NotificationAnnouncingMode.HIGH)

          val combo = comboBox(options, textListCellRenderer {
            notificationModeToUserString[it]
          }).bindItem(settings::getNotificationAnnouncingMode) { settings.notificationAnnouncingMode = it!! }

          if (SystemInfo.isMac) combo.comment(IdeBundle.message("notifications.configurable.announcing.comment"))
        }.visibleIf(screenReaderEnabledProperty)
      }
      row {
        notificationSettings = NotificationSettingsUi(notificationList.model.getElementAt(0), useBalloonNotifications.selected)
        scrollCell(notificationList)
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
    ScrollingUtil.ensureSelectionExists(notificationList)

    ApplicationManager.getApplication().messageBus.connect(this).subscribe(UISettingsListener.TOPIC, UISettingsListener {
      screenReaderEnabledProperty.set(GeneralSettings.getInstance().isSupportScreenReaders)
    })
  }

  private fun createNotificationList(): JBList<NotificationSettingsWrapper> {
    return JBList(*NotificationsConfigurablePanel.NotificationsTreeTableModel().allSettings
      .sortedWith(Comparator { nsw1, nsw2 -> NaturalComparator.INSTANCE.compare(nsw1.toString(), nsw2.toString()) })
      .toTypedArray())
      .apply {
        cellRenderer = textListCellRenderer { it.toString() }
        selectionModel.addListSelectionListener {
          selectedValue?.let { notificationSettings.updateUi(it) }
        }
        selectionMode = ListSelectionModel.SINGLE_SELECTION
      }
  }

  override fun reset(settings: NotificationsConfigurationImpl) {
    ui.reset()
    val selectedIndex = notificationList.selectedIndex
    notificationList.model = createNotificationList().model
    notificationList.selectedIndex = selectedIndex
    notificationSettings.updateUi(notificationList.selectedValue)
    myDoNotAskConfigurableUi.reset()
  }

  override fun isModified(settings: NotificationsConfigurationImpl): Boolean {
    return ui.isModified() || isNotificationsModified() || myDoNotAskConfigurableUi.isModified()
  }

  private fun isNotificationsModified(): Boolean {
    for (i in 0 until notificationList.model.size) {
      if (notificationList.model.getElementAt(i).hasChanged()) {
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
    for (i in 0 until notificationList.model.size) {
      val settingsWrapper = notificationList.model.getElementAt(i)
      if (settingsWrapper.hasChanged()) {
        settingsWrapper.apply()
      }
    }
    myDoNotAskConfigurableUi.apply()
  }

  override fun getComponent(): DialogPanel = ui

  override fun dispose() {}
}
