// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl.ui

import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup.Companion.getGroupTitle
import com.intellij.notification.impl.NotificationSettings
import com.intellij.notification.impl.NotificationsConfigurationImpl
import org.jetbrains.annotations.ApiStatus

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
class NotificationSettingsWrapper internal constructor(var version: NotificationSettings) {
  var isRemoved: Boolean = false
  var title: String? = null
  fun hasChanged(): Boolean {
    return isRemoved || originalSettings != version
  }

  //todo[kb]: reconsider and remove this functionality
  fun remove() {
    isRemoved = true
  }

  val originalSettings: NotificationSettings
    get() = NotificationsConfigurationImpl.getSettings(groupId)

  fun apply() {
    if (isRemoved) {
      NotificationsConfigurationImpl.remove(groupId)
    }
    else {
      NotificationsConfigurationImpl.getInstanceImpl().changeSettings(version)
    }
  }

  fun reset() {
    version = originalSettings
    isRemoved = false
  }

  val groupId: String
    get() = version.groupId

  override fun toString(): String {
    if (title == null) {
      val groupId = groupId
      val title = getGroupTitle(groupId)
      return title ?: groupId
    }
    return title!!
  }

  var isShouldLog: Boolean
    get() = version.isShouldLog
    set(shouldLog) {
      version = version.withShouldLog(shouldLog)
    }

  var isShouldReadAloud: Boolean
    get() = version.isShouldReadAloud
    set(readAloud) {
      version = version.withShouldReadAloud(readAloud)
    }

  var isPlaySound: Boolean
    get() = version.isPlaySound
    set(playSound) {
      version = version.withPlaySound(playSound)
    }

  var displayType: NotificationDisplayType
    get() = version.displayType
    set(type) {
      version = version.withDisplayType(type)
    }

}