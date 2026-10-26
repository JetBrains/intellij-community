// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.productMode

import org.jetbrains.annotations.ApiStatus

/**
 * The environment-configured modules of each [ProductMode] and their availability.
 *
 * The runtime plugin set resolves a `required-if-available` condition only against these modules. The build-time
 * plugin collector applies the same rule, so both read this one table.
 */
@ApiStatus.Internal
object ProductModeEnvironmentModules {
  /**
   * Returns the availability of each environment-configured module in [mode], in registration order.
   */
  fun availability(mode: ProductMode): Map<String, Boolean> {
    val providesBackend = mode == ProductMode.MONOLITH || mode == ProductMode.BACKEND || mode == ProductMode.LANGUAGE_SERVER
    // a monolith process shows the UI, so it provides the frontend module, but it is not a frontend process
    val providesFrontend = mode == ProductMode.MONOLITH || mode == ProductMode.FRONTEND || mode.isLight

    return buildMap {
      this["intellij.platform.frontend"] = providesFrontend
      this["intellij.platform.backend"] = providesBackend
      this["intellij.platform.backend.split"] = mode == ProductMode.BACKEND
      this["intellij.platform.frontend.split.baseline"] = mode.isFrontendProcess
      if (mode.isLight) {
        for (moduleName in listOf("intellij.platform.frontend.split", "intellij.platform.split", "intellij.platform.rpc",
                                  "intellij.rd.client", "intellij.cwm.plugin.common")) {
          this[moduleName] = false
        }
        this["intellij.platform.debugger"] = false
        // the property is temporary
        this["intellij.platform.execution"] = getBooleanProperty("intellij.platform.light.mode.enable.execution", true)
        this["intellij.platform.split.connection"] = mode == ProductMode.LIGHT_WITH_RD_CONNECTION
      }
      else {
        this["intellij.platform.split"] = mode == ProductMode.FRONTEND || mode == ProductMode.BACKEND
        this["intellij.platform.frontend.split"] = mode == ProductMode.FRONTEND
      }
      // this condition is a workaround for LSP-1549
      if (mode != ProductMode.LANGUAGE_SERVER) {
        this["intellij.platform.jps.build.dependencyGraph"] = providesBackend
      }
    }
  }

  private fun getBooleanProperty(key: String, defaultValue: Boolean): Boolean = System.getProperty(key)?.toBoolean() ?: defaultValue
}
