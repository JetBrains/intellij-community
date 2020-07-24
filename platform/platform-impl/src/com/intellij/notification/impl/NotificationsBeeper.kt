// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import java.awt.Toolkit

/**
 * @author Konstantin Bulenkov
 */
internal class NotificationsBeeper: Notifications {
  override fun notify(notification: Notification) {
    if (isSoundEnabled() && NotificationsConfigurationImpl.getSettings(notification.groupId).isPlaySound) {
      Toolkit.getDefaultToolkit().beep()
    }
  }
}