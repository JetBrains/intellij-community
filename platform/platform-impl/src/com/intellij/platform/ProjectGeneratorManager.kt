// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.project.InitProjectGeneratorActivity
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.impl.isCorePlugin
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.coroutineContext

private val LOG = logger<ProjectGeneratorManager>()
private val tracer by lazy { TelemetryManager.getSimpleTracer(Scope("project generator creation")) }

/**
 * Allows running activities before the Create New Project dialog is shown.
 *
 * @see InitProjectGeneratorActivity
 */
@ApiStatus.Internal
class ProjectGeneratorManager {
  suspend fun initProjectGenerator(project: Project?) {
    val componentManager = project ?: ProjectManager.getInstance().defaultProject

    val extensionPoint = (ApplicationManager.getApplication().extensionArea as ExtensionsAreaImpl)
      .getExtensionPoint<InitProjectGeneratorActivity>("com.intellij.initProjectGeneratorActivity")

    for (adapter in extensionPoint.sortedAdapters) {
      coroutineContext.ensureActive()

      val pluginDescriptor = adapter.pluginDescriptor
      val pluginId = pluginDescriptor.pluginId
      if (!isCorePlugin(pluginDescriptor)
          && pluginId.idString != "com.jetbrains.pycharm.pro.customization"
      ) {
        LOG.error("Only bundled plugin can define ${extensionPoint.name}: ${pluginDescriptor}")
        continue
      }

      val activity = adapter.createInstance<InitProjectGeneratorActivity>(componentManager) ?: continue
      withContext(tracer.span("run activity", arrayOf("class", activity.javaClass.name, "plugin", pluginId.idString))) {
        activity.run(project)
      }
    }
  }
}
