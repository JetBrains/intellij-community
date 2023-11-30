// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.openapi.components.Service
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.APP)
@ApiStatus.Internal
class InstantShutdown {

  private var allowed: Boolean = true

  @RequiresEdt
  fun isAllowed(): Boolean = allowed && Registry.`is`("ide.instant.shutdown", true)

  @RequiresEdt
  fun <T> computeWithDisabledInstantShutdown(supplier: () -> T): T {
    val prevValue = allowed
    return try {
      allowed = false
      supplier()
    }
    finally {
      allowed = prevValue
    }
  }
}