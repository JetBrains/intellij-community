// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginAware
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Transient
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

/**
 * Binds a notification group to the sound it plays, to a sound signal, or to both.
 * The class loader of the declaring module reads [sound], so declare the binding in the module that has the sound.
 * Notifications sound on the frontend, so declare a binding in a module that loads there.
 */
@ApiStatus.Internal
class NotificationSoundEP : PluginAware {
  @Attribute("group")
  @JvmField
  @RequiredElement
  var group: @NonNls String = ""

  @Attribute("sound")
  @JvmField
  var sound: @NonNls String? = null

  @Attribute("soundSignal")
  @JvmField
  var soundSignal: @NonNls String? = null

  @Transient
  @JvmField
  var pluginDescriptor: PluginDescriptor? = null

  override fun setPluginDescriptor(pluginDescriptor: PluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor
  }

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<NotificationSoundEP> = ExtensionPointName("com.intellij.notificationSound")
  }
}

internal fun findNotificationSound(groupId: String): NotificationSoundEP? =
  NotificationSoundEP.EP_NAME.extensionList.firstOrNull { it.group == groupId }

internal fun soundSignalIdOf(groupId: String): String? = findNotificationSound(groupId)?.soundSignal

internal fun boundGroupIds(signalId: String): List<String> =
  NotificationSoundEP.EP_NAME.extensionList.filter { it.soundSignal == signalId }.map { it.group }

/** Whether a group bound to [signalId] has a stored Play sound On, which the signal follows until it has a choice of its own. */
internal fun isPlaySoundStored(signalId: String): Boolean =
  boundGroupIds(signalId).any { NotificationsConfigurationImpl.getSettings(it).isPlaySound }
