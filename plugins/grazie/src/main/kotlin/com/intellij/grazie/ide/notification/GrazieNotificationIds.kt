package com.intellij.grazie.ide.notification

import com.intellij.notification.impl.NotificationIdsHolder

internal class GrazieNotificationIds: NotificationIdsHolder {
  override fun getNotificationIds(): List<String> {
    return listOf(GRAZIE_PRO_ADVERTISEMENT)
  }

  companion object {
    const val GRAZIE_PRO_ADVERTISEMENT = "grazie.pro.advertisement"
  }
}
