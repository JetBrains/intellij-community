// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.util

import com.intellij.openapi.util.registry.Registry

object GitLabRegistry {
  fun getRequestPollingIntervalMillis(): Int = Registry.intValue("gitlab.request.polling.interval.millis")
  fun getRequestPollingAttempts(): Int = Registry.intValue("gitlab.request.polling.attempts")
}