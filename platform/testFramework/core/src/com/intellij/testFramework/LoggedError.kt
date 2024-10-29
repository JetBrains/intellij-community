// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class LoggedError(
  message: String?,
  @Suppress("unused") val details: Array<out String>, // already part of [message]
  cause: Throwable?,
) : Throwable(message, cause)
