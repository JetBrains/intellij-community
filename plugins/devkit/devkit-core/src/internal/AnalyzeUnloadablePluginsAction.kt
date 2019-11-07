// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.internal

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.annotate.FileAnnotation
import com.intellij.openapi.vcs.annotate.LineAnnotationAspect
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.text.DateFormatUtil
import org.jetbrains.idea.devkit.dom.ExtensionPoint
import org.jetbrains.idea.devkit.dom.IdeaPlugin
import org.jetbrains.idea.devkit.util.DescriptorUtil
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes

/**
 * @author yole
 */
class AnalyzeUnloadablePluginsAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val view = e.getData(LangDataKeys.IDE_VIEW)
    val dir = view?.orChooseDirectory

    val result = mutableListOf<PluginUnloadabilityStatus>()
    val extensionPointOwners = ExtensionPointOwners()
    val show = ProgressManager.getInstance().runProcessWithProgressSynchronously(
      {
        runReadAction {
          val pi = ProgressManager.getInstance().progressIndicator
          pi.isIndeterminate = false

          val searchScope = when (dir) {
            null -> GlobalSearchScopesCore.projectProductionScope(project)
            else -> GlobalSearchScopesCore.directoryScope(dir, true)
          }
          val pluginXmlFiles = FilenameIndex.getFilesByName(project, PluginManagerCore.PLUGIN_XML, searchScope)

          for ((processed, pluginXmlFile) in pluginXmlFiles.withIndex()) {
            pi.checkCanceled()
            pi.fraction = (processed.toDouble() / pluginXmlFiles.size)

            if (pluginXmlFile !is XmlFile) continue
            if (!ProjectRootManager.getInstance(project).fileIndex.isUnderSourceRootOfType(pluginXmlFile.virtualFile,
                                                                                           JavaModuleSourceRootTypes.PRODUCTION)) {
              continue
            }

            val ideaPlugin = DescriptorUtil.getIdeaPlugin(pluginXmlFile) ?: continue
            val status = analyzeUnloadable(ideaPlugin, extensionPointOwners)
            result.add(status)
            pi.text = status.pluginId
          }
        }
      }, "Analyzing Plugins (${dir?.name ?: "Project"})", true, e.project)

    if (show) showReport(project, result, extensionPointOwners)
    extensionPointOwners.dispose()
  }

  private fun showReport(project: Project, result: List<PluginUnloadabilityStatus>, extensionPointOwners: ExtensionPointOwners) {
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
        it.unspecifiedDynamicEPs.any { !it.startsWith("cidr") && !it.startsWith("appcode") }
      }
      if (closePlugins.isNotEmpty()) {
        appendln("Plugins closest to being unloadable (${closePlugins.size.coerceAtMost(40)} out of ${closePlugins.size}):")
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
        appendln("${pair.second}: ${pair.first} (${extensionPointOwners.getOwner(pair.first)})")
      }

      appendln()
      appendln("EPs grouped by owner:")
      for (owner in extensionPointOwners.getSortedOwners()) {
        val owned = extensionPointOwners.getOwnedEPs(owner)
        appendln("$owner: ${owned.size}")
        for (ep in owned) {
          appendln(ep)
        }
        appendln()
      }
    }

    val fileName = String.format("AnalyzeUnloadablePlugins-Report-%s.txt", DateFormatUtil.formatDateTime(System.currentTimeMillis()))
    val file = LightVirtualFile(fileName, report)
    val descriptor = OpenFileDescriptor(project, file)
    FileEditorManager.getInstance(project).openEditor(descriptor, true)
  }

  private fun analyzeUnloadable(ideaPlugin: IdeaPlugin, extensionPointOwners: ExtensionPointOwners): PluginUnloadabilityStatus {
    val unspecifiedDynamicEPs = mutableSetOf<String>()
    val nonDynamicEPs = mutableSetOf<String>()
    val analysisErrors = mutableListOf<String>()
    var componentCount = analyzePluginFile(ideaPlugin, analysisErrors, nonDynamicEPs, unspecifiedDynamicEPs, extensionPointOwners)

    for (dependency in ideaPlugin.dependencies) {
      val depXmlFile = DescriptorUtil.resolveDependencyToXmlFile(dependency) ?: continue
      val depIdeaPlugin = DescriptorUtil.getIdeaPlugin(depXmlFile) ?: continue
      componentCount += analyzePluginFile(depIdeaPlugin, analysisErrors, nonDynamicEPs, unspecifiedDynamicEPs, extensionPointOwners)
    }

    return PluginUnloadabilityStatus(
      ideaPlugin.pluginId ?: "?",
      unspecifiedDynamicEPs, nonDynamicEPs, componentCount, analysisErrors
    )
  }

  private fun analyzePluginFile(ideaPlugin: IdeaPlugin,
                                analysisErrors: MutableList<String>,
                                nonDynamicEPs: MutableSet<String>,
                                unspecifiedDynamicEPs: MutableSet<String>,
                                extensionPointOwners: ExtensionPointOwners): Int {
    for (extension in ideaPlugin.extensions.flatMap { it.collectExtensions() }) {
      val ep = extension.extensionPoint
      if (ep == null) {
        analysisErrors.add("Cannot resolve EP ${extension.xmlElementName}")
        continue
      }
      extensionPointOwners.discoverOwner(ep)

      when (ep.dynamic.value) {
        false -> nonDynamicEPs.add(ep.effectiveQualifiedName)
        null -> unspecifiedDynamicEPs.add(ep.effectiveQualifiedName)
      }
    }
    val componentCount = ideaPlugin.applicationComponents.flatMap { it.components }.size +
                         ideaPlugin.projectComponents.flatMap { it.components }.size +
                         ideaPlugin.moduleComponents.flatMap { it.components }.size
    return componentCount
  }
}

private data class PluginUnloadabilityStatus(
  val pluginId: String,
  val unspecifiedDynamicEPs: Set<String>,
  val nonDynamicEPs: Set<String>,
  val componentCount: Int,
  val analysisErrors: List<String>
)

private class ExtensionPointOwners {
  private val annotations = mutableMapOf<VirtualFile, FileAnnotation?>()
  private val owners = mutableMapOf<String, String?>()
  private val epsByOwner = mutableMapOf<String, MutableList<String>>()

  fun dispose() {
    annotations.values.filterNotNull().forEach(FileAnnotation::dispose)
  }

  fun getOwner(qName: String) = owners[qName]

  fun discoverOwner(ep: ExtensionPoint) {
    if (ep.effectiveQualifiedName in owners) return
    val vFile = ep.xmlTag.containingFile.virtualFile
    val fileAnnotation = annotations.getOrPut(vFile) {
      val vcs = ProjectLevelVcsManager.getInstance(ep.xmlTag.project).getVcsFor(vFile) ?: return@getOrPut null
      vcs.annotationProvider?.annotate(vFile)
    }
    val authorAspect = fileAnnotation?.aspects?.find { it.id == LineAnnotationAspect.AUTHOR }
    if (authorAspect == null) {
      owners[ep.effectiveQualifiedName] = null
      return
    }

    val tagStartOffset = ep.xmlTag.textRange.startOffset
    val startLine = FileDocumentManager.getInstance().getDocument(vFile)?.getLineNumber(tagStartOffset) ?: return
    val owner = authorAspect.getValue(startLine)
    owners[ep.effectiveQualifiedName] = owner
    epsByOwner.getOrPut(owner) { mutableListOf() }.add(ep.effectiveQualifiedName)
  }

  fun getSortedOwners() = epsByOwner.keys.sortedByDescending { epsByOwner[it]!!.size }
  fun getOwnedEPs(owner: String) = epsByOwner[owner]!!
}
