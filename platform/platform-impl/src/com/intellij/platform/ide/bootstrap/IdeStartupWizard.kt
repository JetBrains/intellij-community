// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.application.Application
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.platform.diagnostic.telemetry.impl.span
import kotlinx.coroutines.Job
import org.jetbrains.annotations.ApiStatus.Internal

internal suspend fun runStartupWizard(isInitialStart: Job, app: Application) {
  val log = logger<IdeStartupWizard>()

  log.info("Entering startup wizard workflow.")

  val point = app.extensionArea
    .getExtensionPoint<IdeStartupWizard>("com.intellij.ideStartupWizard") as ExtensionPointImpl<IdeStartupWizard>
  val sortedAdapters = point.sortedAdapters
  if (sortedAdapters.isEmpty()) {
    log.info("No IdeStartupWizard implementations")
    return
  }

  span("app manager initial state waiting") {
    isInitialStart.join()
  }

  for (adapter in sortedAdapters) {
    val pluginDescriptor = adapter.pluginDescriptor
    if (!pluginDescriptor.isBundled) {
      log.error(PluginException("ideStartupWizard extension can be implemented only by a bundled plugin", pluginDescriptor.pluginId))
      continue
    }

    try {
      log.info("Passing execution control to $adapter.")
      span("${adapter.assignableToClassName}.run") {
        adapter.createInstance<IdeStartupWizard>(app)?.run()
      }
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
