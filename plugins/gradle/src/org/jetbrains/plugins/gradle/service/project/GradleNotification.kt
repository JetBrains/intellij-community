// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object GradleNotification {
  @JvmStatic
  val gradleNotificationGroup: NotificationGroup by lazy(LazyThreadSafetyMode.NONE) {
    NotificationGroupManager.getInstance().getNotificationGroup("Gradle Notification Group")
  }
}
