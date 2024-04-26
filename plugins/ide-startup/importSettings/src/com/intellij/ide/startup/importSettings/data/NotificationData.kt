// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.data

import org.jetbrains.annotations.Nls

interface NotificationData {
  enum class NotificationStatus {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
  }

  val status: NotificationStatus
  val message: String
  val customActionList: List<Action>

  data class Action(val name: @Nls String, val action: () -> Unit)
}