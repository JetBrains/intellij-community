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

  companion object {
    @RequiresEdt
    @JvmStatic
    fun isAllowed(): Boolean {
      if (!Registry.`is`("ide.instant.shutdown", true)) return false
      val application = ApplicationManager.getApplication() ?: return true
      val service = application.getServiceIfCreated<InstantShutdown>(InstantShutdown::class.java) ?: return true
      return service.allowed
    }
  }
}