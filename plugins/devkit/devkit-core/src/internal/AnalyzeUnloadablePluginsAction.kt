// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.internal

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.psi.xml.XmlFile
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.*
import org.jetbrains.idea.devkit.dom.IdeaPlugin
import org.jetbrains.idea.devkit.util.DescriptorUtil
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import javax.swing.JScrollPane
import javax.swing.JTextArea

/**
 * @author yole
 */
class AnalyzeUnloadablePluginsAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val result = mutableListOf<PluginUnloadabilityStatus>()
    val show = ProgressManager.getInstance().runProcessWithProgressSynchronously(
      {
        runReadAction {
          val pi = ProgressManager.getInstance().progressIndicator
          pi.isIndeterminate = false

          val pluginXmlFiles = FilenameIndex.getFilesByName(project, PluginManagerCore.PLUGIN_XML,
                                                            GlobalSearchScopesCore.projectProductionScope(project))
          for ((processed, pluginXmlFile) in pluginXmlFiles.withIndex()) {
            pi.checkCanceled()
            pi.fraction = (processed.toDouble() / pluginXmlFiles.size)

            if (pluginXmlFile !is XmlFile) continue
            if (!ProjectRootManager.getInstance(project).fileIndex.isUnderSourceRootOfType(pluginXmlFile.virtualFile,
                                                                                           JavaModuleSourceRootTypes.PRODUCTION)) {
              continue
            }

            val ideaPlugin = DescriptorUtil.getIdeaPlugin(pluginXmlFile) ?: continue
            val status = analyzeUnloadable(ideaPlugin)
            result.add(status)
            pi.text = status.pluginId
          }
        }
      }, "Analyzing Plugins", true, e.project)

    if (show) showReport(project, result)
  }

  private fun showReport(project: Project, result: List<PluginUnloadabilityStatus>) {
    val report = buildString {
      if (result.any { it.analysisErrors.isNotEmpty() }) {
        appendln("Analysis errors:")
        for (status in result.filter { it.analysisErrors.isNotEmpty() }) {
          appendln(status.pluginId)
          for (analysisError in status.analysisErrors) {
            appendln(analysisError)
          }
          appendln()
        }
      }

      val unloadablePlugins = result.filter { it.componentCount == 0 && it.unspecifiedDynamicEPs.isEmpty() && it.nonDynamicEPs.isEmpty() }
      appendln("Can unload ${unloadablePlugins.size} plugins out of ${result.size}")
      for (status in unloadablePlugins) {
        appendln(status.pluginId)
      }
      appendln()

      val pluginsUsingComponents = result.filter { it.componentCount > 0 }.sortedByDescending { it.componentCount }
      appendln("Plugins using components (${pluginsUsingComponents.size}):")
      for (status in pluginsUsingComponents) {
        appendln("${status.pluginId} (${status.componentCount})")
      }
      appendln()

      val closePlugins = result.filter {
        it.componentCount == 0 &&
        it.nonDynamicEPs.isEmpty() &&
        it.unspecifiedDynamicEPs.isNotEmpty()
      }
      if (closePlugins.isNotEmpty()) {
        appendln("Plugins closest to being unloadable (40 out of ${closePlugins.size}):")
        for (status in closePlugins.sortedBy { it.unspecifiedDynamicEPs.size }.take(40)) {
          appendln("${status.pluginId} - ${status.unspecifiedDynamicEPs.joinToString()}")
        }
        appendln()
      }

      val epUsagesMap = mutableMapOf<String, Int>()
      for (pluginUnloadabilityStatus in result) {
        for (ep in pluginUnloadabilityStatus.unspecifiedDynamicEPs) {
          epUsagesMap[ep] = epUsagesMap.getOrDefault(ep, 0) + 1
        }
      }

      appendln("EP usage statistics (${epUsagesMap.size} non-dynamic EPs remaining):")
      val epUsagesList = epUsagesMap.toList().sortedByDescending { it.second }
      for (pair in epUsagesList) {
        appendln("${pair.second}: ${pair.first}")
      }
    }

    dialog("Plugin Analysis Report", project = project, panel = panel {
      row {
        JScrollPane(JTextArea(report, 20, 80))()
      }
    }).show()
  }

  private fun analyzeUnloadable(ideaPlugin: IdeaPlugin): PluginUnloadabilityStatus {
    val unspecifiedDynamicEPs = mutableSetOf<String>()
    val nonDynamicEPs = mutableSetOf<String>()
    val analysisErrors = mutableListOf<String>()
    for (extension in ideaPlugin.extensions.flatMap { it.collectExtensions() }) {
      val ep = extension.extensionPoint
      if (ep == null) {
        analysisErrors.add("Cannot resolve EP ${extension.xmlElementName}")
        continue
      }
      when (ep.dynamic.value) {
        false -> nonDynamicEPs.add(ep.effectiveQualifiedName)
        null -> unspecifiedDynamicEPs.add(ep.effectiveQualifiedName)
      }
    }
    val componentCount = ideaPlugin.applicationComponents.flatMap { it.components }.size +
                         ideaPlugin.projectComponents.flatMap { it.components }.size +
                         ideaPlugin.moduleComponents.flatMap { it.components }.size
    return PluginUnloadabilityStatus(
      ideaPlugin.pluginId ?: "?",
      unspecifiedDynamicEPs, nonDynamicEPs, componentCount, analysisErrors
    )
  }
}

private data class PluginUnloadabilityStatus(
  val pluginId: String,
  val unspecifiedDynamicEPs: Set<String>,
  val nonDynamicEPs: Set<String>,
  val componentCount: Int,
  val analysisErrors: List<String>
)