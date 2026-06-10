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
    val xmlDescriptor = descriptorFile ?: PluginModuleType.getContentModuleDescriptorXml(module) ?: PluginModuleType.getPluginXml(module)
    if (xmlDescriptor == null) {
      return ModuleAnalysis(ResolvedModuleKind(SplitModeApiRestrictionsService.ModuleKind.SHARED, ""))
    }

    val parsedXmlDescriptor = DescriptorUtil.getIdeaPlugin(xmlDescriptor)
    val predefinedModuleKind =
      SplitModeApiRestrictionsService.getInstance(module.project).getPredefinedModuleKind(module, xmlDescriptor, parsedXmlDescriptor)
    if (predefinedModuleKind != null) {
      return ModuleAnalysis(ResolvedModuleKind(predefinedModuleKind.moduleKind, predefinedModuleKind.reasoning))
    }
    if (parsedXmlDescriptor == null) {
      return ModuleAnalysis(ResolvedModuleKind(SplitModeApiRestrictionsService.ModuleKind.SHARED, ""))
    }

    val mainPluginXmlDescriptor = PluginModuleType.getPluginXml(module)
    val contentModuleXmlDescriptor = PluginModuleType.getContentModuleDescriptorXml(module)
    val shouldAnalyzeContainingPlugins =
      SplitModeAnalysisFlags.isContainingPluginsAnalysisEnabled()
      && isContentModuleDescriptor(xmlDescriptor, descriptorFile, mainPluginXmlDescriptor, contentModuleXmlDescriptor)
    val descriptorAnalysisStates = mutableMapOf<XmlFile, DescriptorDependencyFactsState>()
    val ownDependencyFacts =
      SplitModeDescriptorDependencyAnalyzer.getOrComputeOwnDescriptorDependencyFacts(parsedXmlDescriptor, descriptorAnalysisStates)
    val directDependencyNames = SplitModeDescriptorDependencyAnalyzer.collectDirectDependencyNames(parsedXmlDescriptor).toSet()
    val containingPlugins =
      if (shouldAnalyzeContainingPlugins) {
        analyzeContainingPlugins(collectContainingPlugins(xmlDescriptor), descriptorAnalysisStates)
      }
      else {
        emptyList()
      }

    return computeModuleAnalysis(
      ownDependencyFacts = ownDependencyFacts,
      containingPlugins = containingPlugins,
      hasOwnExplicitFrontendDependency = directDependencyNames.any(::isExplicitFrontendDependency),
      hasOwnExplicitBackendDependency = directDependencyNames.any(::isExplicitBackendDependency),
      hasOwnExplicitMonolithDependency = directDependencyNames.any(::isExplicitMonolithDependency),
      analysisTargetDescription = getAnalysisTargetDescription(module.name, descriptorFile),
    )
  }

  private fun computeModuleAnalysis(
    ownDependencyFacts: DependencyFacts,
    containingPlugins: List<ContainingPlugin>,
    hasOwnExplicitFrontendDependency: Boolean,
    hasOwnExplicitBackendDependency: Boolean,
    hasOwnExplicitMonolithDependency: Boolean,
    analysisTargetDescription: String,
  ): ModuleAnalysis {
    val dependencyAnalysis = DependencyAnalysis(
      analysisTargetDescription = analysisTargetDescription,
      ownFacts = ownDependencyFacts,
      containingPlugins = containingPlugins,
      hasOwnExplicitFrontendDependency = hasOwnExplicitFrontendDependency,
      hasOwnExplicitBackendDependency = hasOwnExplicitBackendDependency,
      hasOwnExplicitMonolithDependency = hasOwnExplicitMonolithDependency,
    )
    return ModuleAnalysis(
      resolvedModuleKind = computeResolvedModuleKind(dependencyAnalysis),
      evidence = dependencyAnalysis.toEvidence(),
    )
  }

  private fun computeResolvedModuleKind(dependencyAnalysis: DependencyAnalysis): ResolvedModuleKind {
    return when {
      dependencyAnalysis.hasOwnExplicitMonolithDependency -> ResolvedModuleKind(
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
      dependencyAnalysis.hasOwnExplicitFrontendDependency -> ResolvedModuleKind(
        SplitModeApiRestrictionsService.ModuleKind.FRONTEND,
        dependencyAnalysis.buildOwnReasoning(SplitModeApiRestrictionsService.ModuleKind.FRONTEND, true),
      )
      dependencyAnalysis.hasOwnExplicitBackendDependency -> ResolvedModuleKind(
        SplitModeApiRestrictionsService.ModuleKind.BACKEND,
        dependencyAnalysis.buildOwnReasoning(SplitModeApiRestrictionsService.ModuleKind.BACKEND, true),
      )
      dependencyAnalysis.hasOwnFrontendEvidence -> ResolvedModuleKind(
        SplitModeApiRestrictionsService.ModuleKind.FRONTEND,
        dependencyAnalysis.buildOwnReasoning(SplitModeApiRestrictionsService.ModuleKind.FRONTEND, false),
      )
      dependencyAnalysis.hasOwnBackendEvidence -> ResolvedModuleKind(
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

  private fun isContentModuleDescriptor(
    xmlDescriptor: XmlFile,
    explicitlyRequestedDescriptor: XmlFile?,
    mainPluginXmlDescriptor: XmlFile?,
    registeredContentModuleXmlDescriptor: XmlFile?,
  ): Boolean {
    if (registeredContentModuleXmlDescriptor?.virtualFile == xmlDescriptor.virtualFile) {
      return true
    }

    val explicitDescriptor = explicitlyRequestedDescriptor ?: return false
    return explicitDescriptor.virtualFile == xmlDescriptor.virtualFile
           && explicitDescriptor.virtualFile != mainPluginXmlDescriptor?.virtualFile
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
        val directDependencyNames = SplitModeDescriptorDependencyAnalyzer.collectDirectDependencyNames(ideaPlugin).toSet()
        val predefinedModuleKind = SplitModeApiRestrictionsService.getInstance(containingModule.project)
          .getPredefinedModuleKind(containingModule, pluginXml, ideaPlugin)
        val resolvedModuleKind =
          if (predefinedModuleKind != null) {
            ResolvedModuleKind(predefinedModuleKind.moduleKind, predefinedModuleKind.reasoning)
          }
          else {
            computeModuleAnalysis(
              ownDependencyFacts = ownDependencyFacts,
              containingPlugins = emptyList(),
              hasOwnExplicitFrontendDependency = directDependencyNames.any(::isExplicitFrontendDependency),
              hasOwnExplicitBackendDependency = directDependencyNames.any(::isExplicitBackendDependency),
              hasOwnExplicitMonolithDependency = directDependencyNames.any(::isExplicitMonolithDependency),
              analysisTargetDescription = getAnalysisTargetDescription(containingModule.name, pluginXml),
            ).resolvedModuleKind
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
  private val analysisTargetDescription: String,
  private val ownFacts: DependencyFacts,
  private val containingPlugins: List<ContainingPlugin>,
  val hasOwnExplicitFrontendDependency: Boolean,
  val hasOwnExplicitBackendDependency: Boolean,
  val hasOwnExplicitMonolithDependency: Boolean,
) {
  private val containingFacts: DependencyFacts = collectContainingPluginFacts(containingPlugins)

  val hasContainingPlugins: Boolean = containingPlugins.isNotEmpty()

  val containingPluginKind: SplitModeApiRestrictionsService.ModuleKind = computeContainingPluginsKind(containingPlugins)

  val hasOwnFrontendEvidence: Boolean = ownFacts.frontendEvidence != null

  val hasOwnBackendEvidence: Boolean = ownFacts.backendEvidence != null

  val hasFrontendDependencies: Boolean =
    hasOwnFrontendEvidence || containingFacts.frontendEvidence != null

  val hasBackendDependencies: Boolean =
    hasOwnBackendEvidence || containingFacts.backendEvidence != null

  val lacksOwnDependencies: Boolean = !hasOwnExplicitFrontendDependency
                                      && !hasOwnExplicitBackendDependency
                                      && !hasOwnExplicitMonolithDependency
                                      && !hasOwnFrontendEvidence
                                      && !hasOwnBackendEvidence

  val hasMixedDependencies: Boolean = !hasOwnExplicitMonolithDependency
                                      && hasFrontendDependencies
                                      && hasBackendDependencies

  fun toEvidence(): ModuleKindEvidence {
    return ModuleKindEvidence(
      hasOwnFrontendEvidence = hasOwnFrontendEvidence,
      hasOwnBackendEvidence = hasOwnBackendEvidence,
      hasOwnExplicitFrontendDependency = hasOwnExplicitFrontendDependency,
      hasOwnExplicitBackendDependency = hasOwnExplicitBackendDependency,
      hasOwnExplicitMonolithDependency = hasOwnExplicitMonolithDependency,
    )
  }

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
      return "No frontend or backend dependencies were found for $analysisTargetDescription"
    }

    return "No frontend or backend dependencies were found among:\n${dependencyNames.joinToString("\n") { dependencyName -> "'$dependencyName'" }}"
  }

  private fun collectOwnReasoningLines(
    kind: SplitModeApiRestrictionsService.ModuleKind,
    explicitOnly: Boolean,
  ): List<String> {
    val lines = mutableListOf<String>()
    val kindName = getReasoningKindName(kind)
    val dependencyInfo = ownFacts.evidence(kind)
    if (dependencyInfo != null && (!explicitOnly || isExplicitDependency(kind, dependencyInfo.name))) {
      lines.add("$kindName dependency '${dependencyInfo.name}' from ${dependencyInfo.originDescription}")
    }
    return lines
  }

  private fun collectDependencyLines(kind: SplitModeApiRestrictionsService.ModuleKind): List<String> {
    val lines = mutableListOf<String>()
    val kindName = getReasoningKindName(kind)
    val dependencyInfo = ownFacts.evidence(kind) ?: containingFacts.evidence(kind)
    if (dependencyInfo != null) {
      lines.add("$kindName dependency '${dependencyInfo.name}' from ${dependencyInfo.originDescription}")
    }
    return lines
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
  for ((_, _, moduleKind) in containingPlugins) {
    if (moduleKind.kind != firstKind) {
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

private fun getAnalysisTargetDescription(moduleName: String, descriptorFile: XmlFile?): String {
  return if (descriptorFile != null) {
    "descriptor '${descriptorFile.name}' in module '$moduleName'"
  }
  else {
    "module '$moduleName'"
  }
}
