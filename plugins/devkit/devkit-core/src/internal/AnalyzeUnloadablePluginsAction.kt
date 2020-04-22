// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.internal

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.annotate.FileAnnotation
import com.intellij.openapi.vcs.annotate.LineAnnotationAspect
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.text.DateFormatUtil
import org.jetbrains.idea.devkit.dom.Dependency
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
            .ifEmpty { FilenameIndex.getFilesByName(project, PluginManagerCore.PLUGIN_XML, GlobalSearchScopesCore.projectProductionScope(project)) }
            .filterIsInstance<XmlFile>()

          for ((processed, pluginXmlFile) in pluginXmlFiles.withIndex()) {
            pi.checkCanceled()
            pi.fraction = (processed.toDouble() / pluginXmlFiles.size)

            if (!ProjectRootManager.getInstance(project).fileIndex.isUnderSourceRootOfType(pluginXmlFile.virtualFile,
                                                                                           JavaModuleSourceRootTypes.PRODUCTION)) {
              continue
            }

            val ideaPlugin = DescriptorUtil.getIdeaPlugin(pluginXmlFile) ?: continue
            val status = analyzeUnloadable(ideaPlugin, extensionPointOwners, pluginXmlFiles)
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

      val unloadablePlugins = result.filter { it.getStatus() == UnloadabilityStatus.UNLOADABLE }
      appendln("Can unload ${unloadablePlugins.size} plugins out of ${result.size}")
      for (status in unloadablePlugins) {
        appendln(status.pluginId)
      }
      appendln()

      val pluginsUsingComponents = result.filter { it.getStatus() == UnloadabilityStatus.USES_COMPONENTS }.sortedByDescending { it.componentCount }
      appendln("Plugins using components (${pluginsUsingComponents.size}):")
      for (status in pluginsUsingComponents) {
        appendln("${status.pluginId} (${status.componentCount})")
      }
      appendln()

      val pluginsUsingServiceOverrides = result.filter { it.getStatus() == UnloadabilityStatus.USES_SERVICE_OVERRIDES }.sortedByDescending { it.serviceOverrides.size }
      appendln("Plugins using service overrides (${pluginsUsingServiceOverrides.size}):")
      for (status in pluginsUsingServiceOverrides) {
        appendln("${status.pluginId} (${status.serviceOverrides.joinToString()})")
      }
      appendln()

      val pluginsWithOptionalDependencies = result.filter { it.getStatus() == UnloadabilityStatus.NON_DYNAMIC_IN_DEPENDENCIES }
      appendln("Plugins not unloadable because of optional dependencies (${pluginsWithOptionalDependencies.size}):")
      for (status in pluginsWithOptionalDependencies) {
        appendln(status.pluginId)
        for ((pluginId, eps) in status.nonDynamicEPsInDependencies) {
          appendln("  ${pluginId} - ${eps.joinToString()}")
        }
      }
      appendln()

      val nonDynamicPlugins = result.filter { it.getStatus() == UnloadabilityStatus.USES_NON_DYNAMIC_EPS }
      if (nonDynamicPlugins.isNotEmpty()) {
        appendln("Plugins with EPs explicitly marked as dynamic=false:")
        for (nonDynamicPlugin in nonDynamicPlugins) {
          appendln("${nonDynamicPlugin.pluginId} (${nonDynamicPlugin.nonDynamicEPs.size})")
          for (ep in nonDynamicPlugin.nonDynamicEPs) {
            appendln("  $ep")
          }
        }
        appendln()
      }

      val closePlugins = result.filter { it.unspecifiedDynamicEPs.any { !it.startsWith("cidr") && !it.startsWith("appcode") } }
      if (closePlugins.isNotEmpty()) {
        appendln("Plugins with non-dynamic EPs (${closePlugins.size}):")
        for (status in closePlugins.sortedBy { it.unspecifiedDynamicEPs.size }) {
          appendln("${status.pluginId} (${status.unspecifiedDynamicEPs.size})")
          for (ep in status.unspecifiedDynamicEPs) {
            appendln("  $ep")
          }
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
        append("${pair.second}: ${pair.first}")
        if (Registry.`is`("analyze.unloadable.discover.owners")) {
          append(" (${extensionPointOwners.getOwner(pair.first)})")
        }
        appendln()
      }

      if (Registry.`is`("analyze.unloadable.discover.owners")) {
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
    }

    val fileName = String.format("AnalyzeUnloadablePlugins-Report-%s.txt", DateFormatUtil.formatDateTime(System.currentTimeMillis()))
    val file = LightVirtualFile(fileName, report)
    val descriptor = OpenFileDescriptor(project, file)
    FileEditorManager.getInstance(project).openEditor(descriptor, true)
  }

  private fun analyzeUnloadable(ideaPlugin: IdeaPlugin, extensionPointOwners: ExtensionPointOwners, allPlugins: List<XmlFile>): PluginUnloadabilityStatus {
    val unspecifiedDynamicEPs = mutableSetOf<String>()
    val nonDynamicEPs = mutableSetOf<String>()
    val analysisErrors = mutableListOf<String>()
    val serviceOverrides = mutableListOf<String>()
    var componentCount = analyzePluginFile(ideaPlugin, analysisErrors, nonDynamicEPs, unspecifiedDynamicEPs, serviceOverrides, extensionPointOwners, true)

    for (dependency in ideaPlugin.dependencies) {
      val configFileName = dependency.configFile.stringValue ?: continue
      val depIdeaPlugin = resolvePluginDependency(dependency)
      if (depIdeaPlugin == null) {
        analysisErrors.add("Failed to resolve dependency descriptor file $configFileName")
        continue
      }
      componentCount += analyzePluginFile(depIdeaPlugin, analysisErrors, nonDynamicEPs, unspecifiedDynamicEPs, serviceOverrides, extensionPointOwners, true)
    }

    val nonDynamicEPsInOptionalDependencies = mutableMapOf<String, MutableSet<String>>()
    val serviceOverridesInDependencies = mutableListOf<String>()
    for (descriptor in allPlugins.mapNotNull { DescriptorUtil.getIdeaPlugin(it) }) {
      for (dependency in descriptor.dependencies) {
        if (dependency.optional.value == true && dependency.value == ideaPlugin) {
          val depIdeaPlugin = resolvePluginDependency(dependency)
          if (depIdeaPlugin == null) {
            if (dependency.configFile.stringValue != null) {
              analysisErrors.add("Failed to resolve dependency descriptor file ${dependency.configFile.stringValue}")
            }
            continue
          }
          val nonDynamicEPsInDependency = mutableSetOf<String>()
          analyzePluginFile(depIdeaPlugin, analysisErrors, nonDynamicEPsInDependency, nonDynamicEPsInDependency, serviceOverridesInDependencies, extensionPointOwners, false)
          if (nonDynamicEPsInDependency.isNotEmpty()) {
            nonDynamicEPsInOptionalDependencies[descriptor.pluginId ?: "<unknown>"] = nonDynamicEPsInDependency
          }
        }
      }
    }

    return PluginUnloadabilityStatus(
      ideaPlugin.pluginId ?: "?",
      unspecifiedDynamicEPs, nonDynamicEPs, nonDynamicEPsInOptionalDependencies, componentCount, serviceOverrides, analysisErrors
    )
  }

  private fun resolvePluginDependency(dependency: Dependency): IdeaPlugin? {
    var xmlFile = DescriptorUtil.resolveDependencyToXmlFile(dependency)
    val configFileName = dependency.configFile.stringValue
    if (xmlFile == null && configFileName != null) {
      val project = dependency.manager.project
      val matchingFiles = FilenameIndex.getFilesByName(project, configFileName, GlobalSearchScopesCore.projectProductionScope(project))
      xmlFile = matchingFiles.singleOrNull() as? XmlFile?
    }
    return xmlFile?.let { DescriptorUtil.getIdeaPlugin(it) }
  }

  private fun analyzePluginFile(ideaPlugin: IdeaPlugin,
                                analysisErrors: MutableList<String>,
                                nonDynamicEPs: MutableSet<String>,
                                unspecifiedDynamicEPs: MutableSet<String>,
                                serviceOverrides: MutableList<String>,
                                extensionPointOwners: ExtensionPointOwners,
                                allowOwnEPs: Boolean): Int {
    for (extension in ideaPlugin.extensions.flatMap { it.collectExtensions() }) {
      val ep = extension.extensionPoint
      if (ep == null) {
        analysisErrors.add("Cannot resolve EP ${extension.xmlElementName}")
        continue
      }
      if (allowOwnEPs && (ep.module == ideaPlugin.module || ep.module == extension.module)) continue  // a plugin can have extensions for its own non-dynamic EPs
      if (Registry.`is`("analyze.unloadable.discover.owners")) {
        extensionPointOwners.discoverOwner(ep)
      }

      when (ep.dynamic.value) {
        false -> nonDynamicEPs.add(ep.effectiveQualifiedName)
        null -> unspecifiedDynamicEPs.add(ep.effectiveQualifiedName)
      }

      if ((ep.effectiveQualifiedName == "com.intellij.applicationService" ||
           ep.effectiveQualifiedName == "com.intellij.projectService" ||
           ep.effectiveQualifiedName == "com.intellij.moduleService") &&
          extension.xmlTag.getAttributeValue("overrides") == "true") {
        serviceOverrides.add(extension.xmlTag.getAttributeValue("serviceInterface") ?: "<unknown>")
      }
    }
    val componentCount = ideaPlugin.applicationComponents.flatMap { it.components }.size +
                         ideaPlugin.projectComponents.flatMap { it.components }.size +
                         ideaPlugin.moduleComponents.flatMap { it.components }.size
    return componentCount
  }
}

enum class UnloadabilityStatus {
  UNLOADABLE, USES_COMPONENTS, USES_SERVICE_OVERRIDES, USES_NON_DYNAMIC_EPS, USES_UNSPECIFIED_DYNAMIC_EPS, NON_DYNAMIC_IN_DEPENDENCIES
}

private data class PluginUnloadabilityStatus(
  val pluginId: String,
  val unspecifiedDynamicEPs: Set<String>,
  val nonDynamicEPs: Set<String>,
  val nonDynamicEPsInDependencies: Map<String, Set<String>>,
  val componentCount: Int,
  val serviceOverrides: List<String>,
  val analysisErrors: List<String>
) {
  fun getStatus(): UnloadabilityStatus {
    return when {
      componentCount > 0 -> UnloadabilityStatus.USES_COMPONENTS
      serviceOverrides.isNotEmpty() -> UnloadabilityStatus.USES_SERVICE_OVERRIDES
      nonDynamicEPs.isNotEmpty() -> UnloadabilityStatus.USES_NON_DYNAMIC_EPS
      unspecifiedDynamicEPs.isNotEmpty() -> UnloadabilityStatus.USES_UNSPECIFIED_DYNAMIC_EPS
      nonDynamicEPsInDependencies.isNotEmpty() -> UnloadabilityStatus.NON_DYNAMIC_IN_DEPENDENCIES
      else -> UnloadabilityStatus.UNLOADABLE
    }
  }
}

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
