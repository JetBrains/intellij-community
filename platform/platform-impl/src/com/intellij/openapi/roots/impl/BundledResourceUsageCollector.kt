// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.ide.plugins.IdeaPluginDescriptor
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
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.NonUrgentExecutor
import com.intellij.util.io.URLUtil
import com.intellij.util.io.exists
import com.intellij.util.io.isDirectory
import org.jetbrains.concurrency.*
import java.nio.file.Path

/**
 * Reports references to resources bundled with IDE or plugin distribution from users' projects (e.g. libraries which include IDE_HOME/lib/junit.jar). 
 */
internal class BundledResourceUsageCollector : ProjectUsagesCollector() {
  companion object {
    val GROUP = EventLogGroup("bundled.resource.reference", 1)

    /**
     * Records path to a file located under 'lib' subdirectory of IDE installation directory and referenced from a library.
     */
    @JvmField
    val IDE_FILE = GROUP.registerEvent("ide.file", EventFields.StringValidatedByCustomRule<BundledResourcePathValidationRule>("path"))

    /**
     * Records path to a file located in an installation directory for a plugin and referenced from a library.
     */
    @JvmField
    val PLUGIN_FILE = GROUP.registerEvent("plugin.file", EventFields.PluginInfo, EventFields.StringValidatedByCustomRule<BundledResourcePathValidationRule>("path"))
  }

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(project: Project, indicator: ProgressIndicator?): CancellablePromise<Set<MetricEvent>> {
    var action = ReadAction.nonBlocking<Set<VirtualFile>> { collectLibraryFiles(project) }
    if (indicator != null) {
      action = action.wrapProgress(indicator)
    }
    @Suppress("UNCHECKED_CAST")
    return action
        .expireWith(project)
        .submit(NonUrgentExecutor.getInstance())
        .thenAsync { files ->
          runAsync {
            files.mapNotNullTo(LinkedHashSet()) { convertToMetric(it.path.substringBefore(URLUtil.JAR_SEPARATOR)) }
          }
        } as CancellablePromise<Set<MetricEvent>>
  }

  private fun collectLibraryFiles(project: Project): Set<VirtualFile> {
    val usedLibraries = LinkedHashSet<Library>()
    OrderEnumerator.orderEntries(project).librariesOnly().forEachLibrary { usedLibraries.add(it) }
    val files = LinkedHashSet<VirtualFile>()
    usedLibraries.flatMapTo(files) { library ->
      library.getFiles(OrderRootType.CLASSES).asList()
    }
    return files
  }

  private val pluginByKindAndDirectory by lazy {
    PluginManagerCore.getLoadedPlugins()
      .filter { it.pluginPath.isDirectory() }
      .associateBy { it.kind to it.pluginPath.fileName.toString() }
  }

  private fun convertToMetric(path: String): MetricEvent? {
    if (FileUtil.isAncestor(ideLibPath, path, false)) {
      val relativePath = FileUtil.getRelativePath(ideLibPath, path, '/')!!
      return IDE_FILE.metric(relativePath)
    }
    for (kind in PluginKind.values()) {
      if (FileUtil.isAncestor(kind.homePath, path, true)) {
        val relativePath = FileUtil.getRelativePath(kind.homePath, path, '/')!!
        val firstName = relativePath.substringBefore('/')
        val pathInPlugin = relativePath.substringAfter('/')
        val plugin = pluginByKindAndDirectory[kind to firstName]
        if (plugin != null) {
          val pluginInfo = getPluginInfoByDescriptor(plugin)
          if (pluginInfo.isSafeToReport()) {
            return PLUGIN_FILE.metric(pluginInfo, pathInPlugin)
          }
        }
      }
    }
    return null
  }
}

private enum class PluginKind {
  Bundled {
    override val homePath: String by lazy { FileUtil.toSystemIndependentName(PathManager.getPreInstalledPluginsPath()) }
  }, 
  Custom {
    override val homePath: String by lazy { FileUtil.toSystemIndependentName(PathManager.getPluginsPath()) }
  };
  
  abstract val homePath: String
}

private val IdeaPluginDescriptor.kind: PluginKind
  get() = if (isBundled) PluginKind.Bundled else PluginKind.Custom

private val ideLibPath by lazy { FileUtil.toSystemIndependentName(PathManager.getLibPath()) }

internal class BundledResourcePathValidationRule : CustomValidationRule() {
  private val pluginKindAndDirectoryById by lazy {
    PluginManagerCore.getLoadedPlugins()
      .filter { it.pluginPath.isDirectory() }
      .associateBy({ it.pluginId.idString }, { it.kind to it.pluginPath.fileName.toString() })
  }

  override fun getRuleId(): String {
    return "bundled_resource_path"
  }

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    if (hasPluginField(context)) {
      if (!isReportedByJetBrainsPlugin(context)) {
        return ValidationResultType.REJECTED
      }
      val (kind, pluginDirectoryName) = pluginKindAndDirectoryById[context.eventData["plugin"]] ?: return ValidationResultType.REJECTED
      if (Path.of(kind.homePath, pluginDirectoryName, data).exists()) {
        return ValidationResultType.ACCEPTED
      }
      return ValidationResultType.REJECTED
    }
    if (Path.of(ideLibPath, data).exists()) {
      return ValidationResultType.ACCEPTED
    }
    return ValidationResultType.REJECTED
  }
}