// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.util.MathUtil
import com.intellij.util.PlatformUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.time.Duration.Companion.milliseconds

internal suspend fun runStartupWizard(isInitialStart: Job, app: Application) {
  val log = logger<IdeStartupWizard>()

  log.info("Entering startup wizard workflow.")

  val point = app.extensionArea
    .getExtensionPoint<IdeStartupWizard>("com.intellij.ideStartupWizard") as ExtensionPointImpl<IdeStartupWizard>
  val sortedAdapters = point.sortedAdapters
  for (adapter in sortedAdapters) {
    val pluginDescriptor = adapter.pluginDescriptor
    if (!pluginDescriptor.isBundled) {
      log.error(PluginException("ideStartupWizard extension can be implemented only by a bundled plugin", pluginDescriptor.pluginId))
      continue
    }

    try {
      val wizard = adapter.createInstance<IdeStartupWizard>(app) ?: continue
      val timeoutMs = System.getProperty("intellij.startup.wizard.initial.timeout").orEmpty().toIntOrNull() ?: 2000

      span("app manager initial state waiting") {
        try {
          withTimeout(timeoutMs.milliseconds) {
            isInitialStart.join()
          }
        } catch (_: TimeoutCancellationException) {
          log.warn("Timeout on waiting for initial start, proceeding without waiting, disabling the startup flow")
          com.intellij.platform.ide.bootstrap.isInitialStart = null
        }
      }

      log.info("Passing execution control to $adapter.")
      span("${adapter.assignableToClassName}.run") {
        wizard.run()
      }

      // first wizard wins
      break
    }
    catch (e: Throwable) {
      log.error(PluginException(e, pluginDescriptor.pluginId))
    }
  }
  point.reset()
}

@Internal
interface IdeStartupWizard {
  suspend fun run()
}
