// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.URLUtil
import com.intellij.util.io.exists
import com.intellij.util.io.isDirectory
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Reports references to resources bundled with IDE or plugin distribution from users' projects (e.g. libraries which include IDE_HOME/lib/junit.jar). 
 */
internal class BundledResourceUsageCollector : ProjectUsagesCollector() {
  companion object {
    val GROUP = EventLogGroup("bundled.resource.reference", 1)

    /**
     * Records path to a file located in IDE installation directory or a bundled plugin and referenced from a library.
     */
    @JvmField
    val IDE_FILE = GROUP.registerEvent("ide.file", EventFields.StringValidatedByCustomRule<BundledResourcePathValidationRule>("path"))

    /**
     * Records path to a file located in an installation directory for a non-bundled plugin and referenced from a library.
     */
    @JvmField
    val PLUGIN_FILE = GROUP.registerEvent("plugin.file", EventFields.PluginInfo, EventFields.StringValidatedByCustomRule<BundledResourcePathValidationRule>("path"))
  }

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(project: Project): MutableSet<MetricEvent> {
    val usedLibraries = LinkedHashSet<Library>()
    OrderEnumerator.orderEntries(project).librariesOnly().forEachLibrary { usedLibraries.add(it) }
    val metrics = LinkedHashSet<MetricEvent>()
    usedLibraries.forEach { library ->
      library.getFiles(OrderRootType.CLASSES).mapNotNullTo(metrics) { file -> 
        convertToMetric(file.path.substringBefore(URLUtil.JAR_SEPARATOR)) 
      } 
    }
    return metrics
  }

  override fun requiresReadAccess(): Boolean = true

  private val pluginByDirectory by lazy {
    PluginManagerCore.getLoadedPlugins()
      .filter { !it.isBundled && it.pluginPath.isDirectory() }
      .associateBy { it.pluginPath.fileName.toString() }
  }

  private fun convertToMetric(path: String): MetricEvent? {
    if (FileUtil.isAncestor(ideHomePath, path, false)) {
      val relativePath = FileUtil.getRelativePath(ideHomePath, path, '/')!!
      return IDE_FILE.metric(relativePath)
    }
    if (FileUtil.isAncestor(pluginsHomePath, path, true)) {
      val relativePath = FileUtil.getRelativePath(pluginsHomePath, path, '/')!!
      val firstName = relativePath.substringBefore('/')
      val pathInPlugin = relativePath.substringAfter('/')
      val plugin = pluginByDirectory[firstName]
      if (plugin != null) {
        val pluginInfo = getPluginInfoByDescriptor(plugin)
        if (pluginInfo.isSafeToReport()) {
          return PLUGIN_FILE.metric(pluginInfo, pathInPlugin)
        }
      }
    }
    return null
  }
}

private val ideHomePath by lazy { FileUtil.toSystemIndependentName(PathManager.getHomePath()) }
private val pluginsHomePath by lazy { FileUtil.toSystemIndependentName(PathManager.getPluginsPath()) }

internal class BundledResourcePathValidationRule : CustomValidationRule() {
  private val pluginDirectoryById by lazy {
    PluginManagerCore.getLoadedPlugins()
      .filter { !it.isBundled && it.pluginPath.isDirectory() }
      .associateBy({ it.pluginId.idString }, { it.pluginPath.fileName.toString() })
  }

  override fun getRuleId(): String {
    return "bundled_resource_path"
  }

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    if (hasPluginField(context)) {
      if (!isReportedByJetBrainsPlugin(context)) {
        return ValidationResultType.REJECTED
      }
      val pluginDirectoryName = pluginDirectoryById[context.eventData["plugin"]]
      if (Path.of(pluginsHomePath, pluginDirectoryName, data).exists()) {
        return ValidationResultType.ACCEPTED
      }
      return ValidationResultType.REJECTED
    }
    if (Path.of(ideHomePath, data).exists()) {
      return ValidationResultType.ACCEPTED
    }
    return ValidationResultType.REJECTED
  }
}