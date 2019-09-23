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

  private fun showReport(project: Project, result: MutableList<PluginUnloadabilityStatus>) {
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

      val unloadablePlugins = result.filter { it.componentCount == 0 && it.nonDynamicEPs.isEmpty() }
      appendln("Can unload ${unloadablePlugins.size} plugins:")
      for (status in unloadablePlugins) {
        appendln(status.pluginId)
      }
      appendln()

      appendln("Plugins using components:")
      for (status in result.filter { it.componentCount > 0 }.sortedByDescending { it.componentCount }) {
        appendln("${status.pluginId} (${status.componentCount})")
      }
      appendln()

      val epUsagesMap = mutableMapOf<String, Int>()
      for (pluginUnloadabilityStatus in result) {
        for (ep in pluginUnloadabilityStatus.nonDynamicEPs) {
          epUsagesMap[ep] = epUsagesMap.getOrDefault(ep, 0) + 1
        }
      }

      appendln("EP usage statistics:")
      val epUsagesList = epUsagesMap.toList().sortedByDescending { it.second }
      for (pair in epUsagesList) {
        appendln("${pair.second}: ${pair.first}")
      }
    }

    dialog("Plugin Analysis Report", panel = panel {
      row {
        JScrollPane(JTextArea(report, 20, 80))()
      }
    }).show()
  }

  private fun analyzeUnloadable(ideaPlugin: IdeaPlugin): PluginUnloadabilityStatus {
    val nonDynamicEPs = mutableSetOf<String>()
    val analysisErrors = mutableListOf<String>()
    for (extension in ideaPlugin.extensions.flatMap { it.collectExtensions() }) {
      val ep = extension.extensionPoint
      if (ep == null) {
        analysisErrors.add("Cannot resolve EP ${extension.xmlElementName}")
        continue
      }
      if (ep.dynamic.value != true) {
        nonDynamicEPs.add(ep.effectiveQualifiedName)
      }
    }
    val componentCount = ideaPlugin.applicationComponents.flatMap { it.components }.size +
                         ideaPlugin.projectComponents.flatMap { it.components }.size +
                         ideaPlugin.moduleComponents.flatMap { it.components }.size
    return PluginUnloadabilityStatus(ideaPlugin.pluginId ?: "?", nonDynamicEPs, componentCount, analysisErrors)
  }
}

private data class PluginUnloadabilityStatus(
  val pluginId: String,
  val nonDynamicEPs: Set<String>,
  val componentCount: Int,
  val analysisErrors: List<String>
)