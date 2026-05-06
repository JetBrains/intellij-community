// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.remotedev.analysis

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.xml.XmlFile
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeInspectionUtil.findDependingContentModuleEntriesInFile
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.jetbrains.idea.devkit.util.DescriptorUtil

internal object SplitModeModuleKindResolver {
  private val LOG = logger<SplitModeModuleKindResolver>()

  fun doesApiKindMatchExpectedModuleKind(
    actualApiUsageModuleKind: ResolvedModuleKind,
    expectedKind: SplitModeApiRestrictionsService.ModuleKind,
  ): Boolean {
    return expectedKind.accepts(actualApiUsageModuleKind.kind)
  }

  fun getOrComputeModuleAnalysis(module: Module, descriptorFile: XmlFile? = null): ModuleAnalysis {
    val predefinedModuleKind = SplitModeApiRestrictionsService.getInstance().getPredefinedModuleKind(module, descriptorFile)
    if (predefinedModuleKind != null) {
      return ModuleAnalysis(ResolvedModuleKind(predefinedModuleKind.moduleKind, predefinedModuleKind.reasoning))
    }

    return computeModuleAnalysis(module)
  }

  private fun computeModuleAnalysis(
    module: Module,
  ): ModuleAnalysis {
    val contentModuleXmlDescriptor = PluginModuleType.getContentModuleDescriptorXml(module)
    val xmlDescriptor = contentModuleXmlDescriptor ?: PluginModuleType.getPluginXml(module)
    if (xmlDescriptor == null) {
      return ModuleAnalysis(ResolvedModuleKind(SplitModeApiRestrictionsService.ModuleKind.SHARED, ""))
    }

    val parsedXmlDescriptor = DescriptorUtil.getIdeaPlugin(xmlDescriptor)
    val predefinedModuleKind = SplitModeApiRestrictionsService.getInstance().getPredefinedModuleKind(module, xmlDescriptor, parsedXmlDescriptor)
    if (predefinedModuleKind != null) {
      return ModuleAnalysis(ResolvedModuleKind(predefinedModuleKind.moduleKind, predefinedModuleKind.reasoning))
    }
    if (parsedXmlDescriptor == null) {
      return ModuleAnalysis(ResolvedModuleKind(SplitModeApiRestrictionsService.ModuleKind.SHARED, ""))
    }

    val descriptorAnalysisStates = mutableMapOf<XmlFile, DescriptorDependencyFactsState>()
    val ownDependencyFacts = SplitModeDescriptorDependencyAnalyzer.getOrComputeOwnDescriptorDependencyFacts(parsedXmlDescriptor, descriptorAnalysisStates)
    val containingPlugins =
      if (contentModuleXmlDescriptor != null && SplitModeAnalysisFlags.isContainingPluginsAnalysisEnabled()) {
        analyzeContainingPlugins(collectContainingPlugins(contentModuleXmlDescriptor), descriptorAnalysisStates)
      }
      else {
        emptyList()
      }

    return ModuleAnalysis(computeResolvedModuleKind(module.name, ownDependencyFacts, containingPlugins))
  }

  private fun computeResolvedModuleKind(
    moduleName: String,
    ownDependencyFacts: DependencyFacts,
    containingPlugins: List<ContainingPlugin>,
  ): ResolvedModuleKind {
    return computeResolvedModuleKind(DependencyAnalysis(moduleName, ownDependencyFacts, containingPlugins))
  }

  private fun computeResolvedModuleKind(dependencyAnalysis: DependencyAnalysis): ResolvedModuleKind {
    return when {
      dependencyAnalysis.declaresExplicitMonolithDependency -> ResolvedModuleKind(
        SplitModeApiRestrictionsService.ModuleKind.MONOLITH,
        dependencyAnalysis.buildOwnReasoning(SplitModeApiRestrictionsService.ModuleKind.MONOLITH, true),
      )
      dependencyAnalysis.lacksOwnDependencies
      && dependencyAnalysis.hasContainingPlugins
      && dependencyAnalysis.containingPluginKind == SplitModeApiRestrictionsService.ModuleKind.FRONTEND -> ResolvedModuleKind(
        SplitModeApiRestrictionsService.ModuleKind.FRONTEND,
        dependencyAnalysis.buildContainingPluginsReasoning(),
      )
      dependencyAnalysis.lacksOwnDependencies
      && dependencyAnalysis.hasContainingPlugins
      && dependencyAnalysis.containingPluginKind == SplitModeApiRestrictionsService.ModuleKind.BACKEND -> ResolvedModuleKind(
        SplitModeApiRestrictionsService.ModuleKind.BACKEND,
        dependencyAnalysis.buildContainingPluginsReasoning(),
      )
      dependencyAnalysis.lacksOwnDependencies && dependencyAnalysis.hasContainingPlugins -> ResolvedModuleKind(
        SplitModeApiRestrictionsService.ModuleKind.SHARED,
        dependencyAnalysis.buildContainingPluginsReasoning(),
      )
      dependencyAnalysis.hasMixedDependencies -> ResolvedModuleKind(
        SplitModeApiRestrictionsService.ModuleKind.MIXED,
        dependencyAnalysis.buildMixedReasoning(),
      )
      dependencyAnalysis.declaresExplicitFrontendDependencies -> ResolvedModuleKind(
        SplitModeApiRestrictionsService.ModuleKind.FRONTEND,
        dependencyAnalysis.buildOwnReasoning(SplitModeApiRestrictionsService.ModuleKind.FRONTEND, true),
      )
      dependencyAnalysis.declaresExplicitBackendDependencies -> ResolvedModuleKind(
        SplitModeApiRestrictionsService.ModuleKind.BACKEND,
        dependencyAnalysis.buildOwnReasoning(SplitModeApiRestrictionsService.ModuleKind.BACKEND, true),
      )
      dependencyAnalysis.declaresFrontendDependencies -> ResolvedModuleKind(
        SplitModeApiRestrictionsService.ModuleKind.FRONTEND,
        dependencyAnalysis.buildOwnReasoning(SplitModeApiRestrictionsService.ModuleKind.FRONTEND, false),
      )
      dependencyAnalysis.declaresBackendDependencies -> ResolvedModuleKind(
        SplitModeApiRestrictionsService.ModuleKind.BACKEND,
        dependencyAnalysis.buildOwnReasoning(SplitModeApiRestrictionsService.ModuleKind.BACKEND, false),
      )
      else -> ResolvedModuleKind(
        SplitModeApiRestrictionsService.ModuleKind.SHARED,
        dependencyAnalysis.buildSharedReasoning(),
      )
    }
  }

  private fun collectContainingPlugins(contentModuleDescriptor: XmlFile): List<XmlFile> {
    return findDependingContentModuleEntriesInFile(contentModuleDescriptor)
      .mapNotNull { it.xmlElement?.containingFile as? XmlFile }
      .distinctBy { it.virtualFile.path }
      .sortedBy { it.virtualFile.path }
      .toList()
  }

  private fun analyzeContainingPlugins(
    containingPluginXmlFiles: List<XmlFile>,
    descriptorAnalysisStates: MutableMap<XmlFile, DescriptorDependencyFactsState>,
  ): List<ContainingPlugin> {
    val containingPlugins = sequence {
      for (pluginXml in containingPluginXmlFiles) {
        val ideaPlugin = DescriptorUtil.getIdeaPlugin(pluginXml) ?: continue
        val containingModule = ModuleUtilCore.findModuleForPsiElement(pluginXml) ?: continue
        val ownDependencyFacts =
          SplitModeDescriptorDependencyAnalyzer.getOrComputeOwnDescriptorDependencyFacts(ideaPlugin, descriptorAnalysisStates)
        val predefinedModuleKind = SplitModeApiRestrictionsService.getInstance().getPredefinedModuleKind(containingModule, pluginXml, ideaPlugin)
        val resolvedModuleKind =
          if (predefinedModuleKind != null) {
            ResolvedModuleKind(predefinedModuleKind.moduleKind, predefinedModuleKind.reasoning)
          }
          else {
            computeResolvedModuleKind(containingModule.name, ownDependencyFacts, emptyList())
          }

        yield(
          ContainingPlugin(
            descriptorPath = pluginXml.virtualFile.path,
            moduleName = containingModule.name,
            moduleKind = resolvedModuleKind,
            dependencyFacts = asContainingPluginFacts(ownDependencyFacts),
          )
        )
      }
    }.toList()

    if (containingPlugins.isNotEmpty()) {
      LOG.debug {
        "Content module is included by plugin descriptors: " +
        containingPlugins.joinToString { containingPlugin ->
          "${containingPlugin.descriptorPath} [module=${containingPlugin.moduleName}, kind=${containingPlugin.moduleKind.kind.id}]"
        }
      }
    }

    return containingPlugins
  }
}

private data class ContainingPlugin(
  val descriptorPath: String,
  val moduleName: String,
  val moduleKind: ResolvedModuleKind,
  val dependencyFacts: DependencyFacts,
)

private class DependencyAnalysis(
  private val moduleName: String,
  private val ownFacts: DependencyFacts,
  private val containingPlugins: List<ContainingPlugin>,
) {
  private val containingFacts: DependencyFacts = collectContainingPluginFacts(containingPlugins)
  private val isFrontendModuleByConvention = isFrontendModuleName(moduleName)
  private val isBackendModuleByConvention = isBackendModuleName(moduleName)
  private val ownFrontendEvidence = ownFacts.frontendEvidence
  private val ownBackendEvidence = ownFacts.backendEvidence

  val hasContainingPlugins: Boolean = containingPlugins.isNotEmpty()

  val containingPluginKind: SplitModeApiRestrictionsService.ModuleKind = computeContainingPluginsKind(containingPlugins)

  val declaresExplicitFrontendDependencies: Boolean =
    isFrontendModuleByConvention || ownFrontendEvidence != null && isExplicitFrontendDependency(ownFrontendEvidence.name)

  val declaresExplicitBackendDependencies: Boolean =
    isBackendModuleByConvention || ownBackendEvidence != null && isExplicitBackendDependency(ownBackendEvidence.name)

  val declaresExplicitMonolithDependency: Boolean = ownFacts.monolithEvidence != null

  val declaresFrontendDependencies: Boolean = ownFacts.frontendEvidence != null

  val declaresBackendDependencies: Boolean = ownFacts.backendEvidence != null

  val hasFrontendDependencies: Boolean =
    isFrontendModuleByConvention || ownFacts.frontendEvidence != null || containingFacts.frontendEvidence != null

  val hasBackendDependencies: Boolean =
    isBackendModuleByConvention || ownFacts.backendEvidence != null || containingFacts.backendEvidence != null

  val lacksOwnDependencies: Boolean = !declaresExplicitFrontendDependencies
                                      && !declaresExplicitBackendDependencies
                                      && !declaresExplicitMonolithDependency
                                      && !declaresFrontendDependencies
                                      && !declaresBackendDependencies

  val hasMixedDependencies: Boolean = !declaresExplicitMonolithDependency
                                      && (isFrontendModuleByConvention || hasFrontendDependencies)
                                      && (isBackendModuleByConvention || hasBackendDependencies)

  fun buildOwnReasoning(kind: SplitModeApiRestrictionsService.ModuleKind, explicitOnly: Boolean): String {
    return collectOwnReasoningLines(kind, explicitOnly).joinToString("\n")
  }

  fun buildMixedReasoning(): String {
    val frontendLines = collectDependencyLines(SplitModeApiRestrictionsService.ModuleKind.FRONTEND)
    val backendLines = collectDependencyLines(SplitModeApiRestrictionsService.ModuleKind.BACKEND)
    val sections = mutableListOf<String>()
    if (frontendLines.isNotEmpty()) {
      sections.add(frontendLines.joinToString("\n"))
    }
    if (backendLines.isNotEmpty()) {
      sections.add(backendLines.joinToString("\n"))
    }
    return sections.joinToString("\n\n")
  }

  fun buildContainingPluginsReasoning(): String {
    if (containingPlugins.isEmpty()) {
      return "No containing plugin descriptors were found"
    }

    return "Module declares no own FE/BE dependencies, but the containing plugin.xml files do:\n${
      containingPlugins.sortedBy { it.moduleName }.joinToString("\n") { containingPlugin ->
        "Module '${containingPlugin.moduleName}'  -> ${containingPlugin.moduleKind.kind.id}"
      }
    }"
  }

  fun buildSharedReasoning(): String {
    val dependencyNames = listOfNotNull(
      ownFacts.frontendEvidence?.name,
      ownFacts.backendEvidence?.name,
      ownFacts.monolithEvidence?.name,
      containingFacts.frontendEvidence?.name,
      containingFacts.backendEvidence?.name,
      containingFacts.monolithEvidence?.name,
    ).distinct()
    if (dependencyNames.isEmpty()) {
      return "No frontend or backend dependencies were found for module '$moduleName'"
    }

    return "No frontend or backend dependencies were found among:\n${dependencyNames.joinToString("\n") { dependencyName -> "'$dependencyName'" }}"
  }

  private fun collectOwnReasoningLines(
    kind: SplitModeApiRestrictionsService.ModuleKind,
    explicitOnly: Boolean,
  ): List<String> {
    val lines = mutableListOf<String>()
    val kindName = getReasoningKindName(kind)
    if (followsModuleKindNamingConvention(kind)) {
      lines.add("$kindName indicator: module name '$moduleName'")
    }
    val dependencyInfo = ownFacts.evidence(kind)
    if (dependencyInfo != null && (!explicitOnly || isExplicitDependency(kind, dependencyInfo.name))) {
      lines.add("$kindName dependency '${dependencyInfo.name}' from ${dependencyInfo.originDescription}")
    }
    return lines
  }

  private fun collectDependencyLines(kind: SplitModeApiRestrictionsService.ModuleKind): List<String> {
    val lines = mutableListOf<String>()
    val kindName = getReasoningKindName(kind)
    if (followsModuleKindNamingConvention(kind)) {
      lines.add("$kindName indicator: module name '$moduleName'")
    }
    val dependencyInfo = ownFacts.evidence(kind) ?: containingFacts.evidence(kind)
    if (dependencyInfo != null) {
      lines.add("$kindName dependency '${dependencyInfo.name}' from ${dependencyInfo.originDescription}")
    }
    return lines
  }

  private fun followsModuleKindNamingConvention(kind: SplitModeApiRestrictionsService.ModuleKind): Boolean {
    return when (kind) {
      SplitModeApiRestrictionsService.ModuleKind.FRONTEND -> isFrontendModuleByConvention
      SplitModeApiRestrictionsService.ModuleKind.BACKEND -> isBackendModuleByConvention
      else -> false
    }
  }

  private fun isExplicitDependency(kind: SplitModeApiRestrictionsService.ModuleKind, dependencyName: String): Boolean {
    return when (kind) {
      SplitModeApiRestrictionsService.ModuleKind.FRONTEND -> isExplicitFrontendDependency(dependencyName)
      SplitModeApiRestrictionsService.ModuleKind.BACKEND -> isExplicitBackendDependency(dependencyName)
      SplitModeApiRestrictionsService.ModuleKind.MONOLITH -> isExplicitMonolithDependency(dependencyName)
      else -> false
    }
  }

  private fun getReasoningKindName(kind: SplitModeApiRestrictionsService.ModuleKind): String {
    return when (kind) {
      SplitModeApiRestrictionsService.ModuleKind.FRONTEND -> "Frontend"
      SplitModeApiRestrictionsService.ModuleKind.BACKEND -> "Backend"
      SplitModeApiRestrictionsService.ModuleKind.MONOLITH -> "Monolith"
      else -> error("Unsupported module kind for reasoning: $kind")
    }
  }
}

private fun computeContainingPluginsKind(containingPlugins: List<ContainingPlugin>): SplitModeApiRestrictionsService.ModuleKind {
  if (containingPlugins.isEmpty()) {
    return SplitModeApiRestrictionsService.ModuleKind.SHARED
  }

  val firstKind = containingPlugins.first().moduleKind.kind
  for (containingPlugin in containingPlugins) {
    if (containingPlugin.moduleKind.kind != firstKind) {
      return SplitModeApiRestrictionsService.ModuleKind.MIXED
    }
  }
  return firstKind
}

private fun collectContainingPluginFacts(containingPlugins: List<ContainingPlugin>): DependencyFacts {
  return DependencyFacts(
    frontendEvidence = containingPlugins.firstNotNullOfOrNull { it.dependencyFacts.frontendEvidence },
    backendEvidence = containingPlugins.firstNotNullOfOrNull { it.dependencyFacts.backendEvidence },
    monolithEvidence = containingPlugins.firstNotNullOfOrNull { it.dependencyFacts.monolithEvidence },
  )
}

private fun asContainingPluginFacts(dependencyFacts: DependencyFacts): DependencyFacts {
  return dependencyFacts.mapTraces { trace ->
    DependencyTrace(
      cacheKey = "containing|${trace.cacheKey}",
      description = "containing plugin ${trace.description}",
    )
  }
}
