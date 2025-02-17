// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.users

import com.intellij.util.SystemProperties
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.ScheduledForRemoval
@Deprecated("Use `com.intellij.util.SystemProperties.getUserName`", level = DeprecationLevel.ERROR)
object LocalUserSettings {
  val userName: String
    get() = SystemProperties.getUserName()
}
