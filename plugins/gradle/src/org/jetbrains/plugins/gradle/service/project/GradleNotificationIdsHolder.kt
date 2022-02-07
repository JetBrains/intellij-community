// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.notification.impl.NotificationIdsHolder

class GradleNotificationIdsHolder : NotificationIdsHolder {
  companion object {
    const val jvmConfigured = "gradle.jvm.configured"
    const val jvmInvalid = "gradle.jvm.invalid"
    const val configurationError = "gradle.configuration.error"
  }

  override fun getNotificationIds(): List<String> {
    return listOf(
      jvmConfigured,
      jvmInvalid,
      configurationError,
    )
  }
}