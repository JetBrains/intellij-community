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
 * Binds a notification group to the sound it plays while its Play sound option is on.
 * The class loader of the declaring module reads [sound], so declare the binding in the module that has the sound.
 */
@ApiStatus.Internal
class NotificationSoundEP : PluginAware {
  @Attribute("group")
  @JvmField
  @RequiredElement
  var group: @NonNls String = ""

  @Attribute("sound")
  @JvmField
  @RequiredElement
  var sound: @NonNls String = ""

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
