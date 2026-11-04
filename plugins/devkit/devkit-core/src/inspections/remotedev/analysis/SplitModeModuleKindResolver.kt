// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.remotedev.analysis

import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.xml.XmlFile
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeInspectionUtil.findDependingContentModuleEntriesInFile
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.jetbrains.idea.devkit.util.DescriptorUtil

private val MODULE_ID_ANALYSIS_KEY =
  Key.create<CachedValue<ModuleAnalysis>>("SplitModeModuleKindResolver.moduleIdAnalysis")

private val MODULE_DESCRIPTOR_ANALYSIS_KEY =
  Key.create<CachedValue<ModuleAnalysis>>("SplitModeModuleKindResolver.moduleDescriptorAnalysis")

internal object SplitModeModuleKindResolver {
  private val LOG = logger<SplitModeModuleKindResolver>()

  fun doesApiKindMatchExpectedModuleKind(
    actualApiUsageModuleKind: ResolvedModuleKind,
    expectedKind: SplitModeApiRestrictionsService.ModuleKind,
  ): Boolean {
    return expectedKind.accepts(actualApiUsageModuleKind.kind)
  }

  fun isApiUsageAllowed(
    actualModuleAnalysis: ModuleAnalysis,
    apiRestriction: SplitModeApiRestrictionsService.ApiRestrictionMatch,
  ): Boolean {
    val actualModuleKind = actualModuleAnalysis.resolvedModuleKind
    return doesApiKindMatchExpectedModuleKind(actualModuleKind, apiRestriction.targetModuleKind)
           || actualModuleKind.kind == SplitModeApiRestrictionsService.ModuleKind.BACKEND
           && actualModuleAnalysis.apiUsagePolicy == ApiUsagePolicy.BACKEND_WITH_NON_UI_API_PERMIT
           && apiRestriction.restrictionKind == SplitModeApiRestrictionsService.ApiRestrictionKind.GENERIC_PLATFORM_API
  }

  /**
   * Returns the analysis of the own descriptor of [module].
   *
   * The platform holds the result in the user data of [module].
   * Therefore, the provider must capture no PSI element.
   */
  fun getOrComputeModuleAnalysis(module: Module): ModuleAnalysis {
    if (findOwnDescriptor(module) == null) {
      return ModuleAnalysis(ResolvedModuleKind(SplitModeApiRestrictionsService.ModuleKind.SHARED, ModuleKindReason.None))
    }

    return CachedValuesManager.getManager(module.project).getCachedValue(module, MODULE_ID_ANALYSIS_KEY, {
      val project = module.project
      Result.create(
        computeModuleAnalysis(module, null),
        ProjectRootModificationTracker.getInstance(project),
        PsiManager.getInstance(project).modificationTracker.forLanguage(XMLLanguage.INSTANCE),
        SplitModeApiRestrictionsService.getInstance(project).getModificationTracker(),
      )
    }, false)
  }

  /**
   * Returns the analysis of [descriptorFile].
   *
   * The platform holds the result in the user data of [descriptorFile].
   * Therefore, the provider must capture no module, and it resolves the module itself.
   * A module is not a stable function of a descriptor file.
   */
  fun getOrComputeDescriptorAnalysis(descriptorFile: XmlFile): ModuleAnalysis {
    return CachedValuesManager.getManager(descriptorFile.project).getCachedValue(descriptorFile, MODULE_DESCRIPTOR_ANALYSIS_KEY, {
      val project = descriptorFile.project
      Result.create(
        computeDescriptorAnalysis(descriptorFile),
        ProjectRootModificationTracker.getInstance(project),
        PsiManager.getInstance(project).modificationTracker.forLanguage(XMLLanguage.INSTANCE),
        SplitModeApiRestrictionsService.getInstance(project).getModificationTracker(),
      )
    }, false)
  }

  private fun computeDescriptorAnalysis(descriptorFile: XmlFile): ModuleAnalysis {
    val module = ModuleUtilCore.findModuleForPsiElement(descriptorFile) ?: return ModuleAnalysis(ResolvedModuleKind(SplitModeApiRestrictionsService.ModuleKind.SHARED, ModuleKindReason.None))
    return computeModuleAnalysis(module, descriptorFile)
  }

  private fun findOwnDescriptor(module: Module): XmlFile? {
    return PluginModuleType.getContentModuleDescriptorXml(module) ?: PluginModuleType.getPluginXml(module)
  }

  private fun computeModuleAnalysis(module: Module, descriptorFile: XmlFile?): ModuleAnalysis {
    val xmlDescriptor = descriptorFile ?: findOwnDescriptor(module)
    if (xmlDescriptor == null) {
      return ModuleAnalysis(ResolvedModuleKind(SplitModeApiRestrictionsService.ModuleKind.SHARED, ModuleKindReason.None))
    }

    val parsedXmlDescriptor = DescriptorUtil.getIdeaPlugin(xmlDescriptor)
    val predefinedModuleKind =
      SplitModeApiRestrictionsService.getInstance(module.project).getPredefinedModuleKind(module, xmlDescriptor, parsedXmlDescriptor)
    if (predefinedModuleKind != null) {
      return ModuleAnalysis(
        resolvedModuleKind = ResolvedModuleKind(predefinedModuleKind.moduleKind, ModuleKindReason.Predefined(predefinedModuleKind.reasoning)),
        apiUsagePolicy = predefinedModuleKind.apiUsagePolicy,
      )
    }
    if (parsedXmlDescriptor == null) {
      return ModuleAnalysis(ResolvedModuleKind(SplitModeApiRestrictionsService.ModuleKind.SHARED, ModuleKindReason.None))
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
      analysisTarget = AnalysisTarget(module.name, descriptorFile?.name),
    )
  }

  private fun computeModuleAnalysis(
    ownDependencyFacts: DependencyFacts,
    containingPlugins: List<ContainingPlugin>,
    hasOwnExplicitFrontendDependency: Boolean,
    hasOwnExplicitBackendDependency: Boolean,
    hasOwnExplicitMonolithDependency: Boolean,
    analysisTarget: AnalysisTarget,
  ): ModuleAnalysis {
    val dependencyAnalysis = DependencyAnalysis(
      analysisTarget = analysisTarget,
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
      && dependencyAnalysis.inheritedModuleKind != null -> ResolvedModuleKind(
        dependencyAnalysis.inheritedModuleKind,
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
      dependencyAnalysis.hasOwnExplicitFrontendDependency -> dependencyAnalysis.resolveFrontendKind(true)
      dependencyAnalysis.hasOwnExplicitBackendDependency -> ResolvedModuleKind(
        SplitModeApiRestrictionsService.ModuleKind.BACKEND,
        dependencyAnalysis.buildOwnReasoning(SplitModeApiRestrictionsService.ModuleKind.BACKEND, true),
      )
      dependencyAnalysis.hasOwnFrontendEvidence -> dependencyAnalysis.resolveFrontendKind(false)
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
            ResolvedModuleKind(predefinedModuleKind.moduleKind, ModuleKindReason.Predefined(predefinedModuleKind.reasoning))
          }
          else {
            computeModuleAnalysis(
              ownDependencyFacts = ownDependencyFacts,
              containingPlugins = emptyList(),
              hasOwnExplicitFrontendDependency = directDependencyNames.any(::isExplicitFrontendDependency),
              hasOwnExplicitBackendDependency = directDependencyNames.any(::isExplicitBackendDependency),
              hasOwnExplicitMonolithDependency = directDependencyNames.any(::isExplicitMonolithDependency),
              analysisTarget = AnalysisTarget(containingModule.name, pluginXml.name),
            ).resolvedModuleKind
          }

        yield(
          ContainingPlugin(
            descriptorPath = pluginXml.virtualFile.path,
            descriptorName = pluginXml.name,
            pluginId = ideaPlugin.pluginId,
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
  val descriptorName: String,
  val pluginId: String?,
  val moduleName: String,
  val moduleKind: ResolvedModuleKind,
  val dependencyFacts: DependencyFacts,
)

private class DependencyAnalysis(
  private val analysisTarget: AnalysisTarget,
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

  private val hasOwnRpcEvidence: Boolean = ownFacts.rpcEvidence != null

  /**
   * The kind that a module without own frontend or backend dependencies takes from its containing plugins.
   * [SplitModeApiRestrictionsService.ModuleKind.LITE_FRONTEND] becomes a regular frontend kind
   * when the module itself depends on `intellij.platform.rpc`.
   */
  val inheritedModuleKind: SplitModeApiRestrictionsService.ModuleKind? = when (containingPluginKind) {
    SplitModeApiRestrictionsService.ModuleKind.FRONTEND,
    SplitModeApiRestrictionsService.ModuleKind.BACKEND,
      -> containingPluginKind
    SplitModeApiRestrictionsService.ModuleKind.LITE_FRONTEND ->
      if (hasOwnRpcEvidence) SplitModeApiRestrictionsService.ModuleKind.FRONTEND else containingPluginKind
    SplitModeApiRestrictionsService.ModuleKind.MONOLITH,
    SplitModeApiRestrictionsService.ModuleKind.MIXED,
    SplitModeApiRestrictionsService.ModuleKind.SHARED,
    is SplitModeApiRestrictionsService.ModuleKind.Composite,
      -> null
  }

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

  fun buildOwnReasoning(kind: SplitModeApiRestrictionsService.ModuleKind, explicitOnly: Boolean): ModuleKindReason {
    return ModuleKindReason.OwnDependency(findOwnEvidence(kind, explicitOnly), isRpcMissing = false)
  }

  /**
   * Resolves a frontend module to [SplitModeApiRestrictionsService.ModuleKind.LITE_FRONTEND]
   * when it does not depend on `intellij.platform.rpc`.
   */
  fun resolveFrontendKind(explicitOnly: Boolean): ResolvedModuleKind {
    val evidence = findOwnEvidence(SplitModeApiRestrictionsService.ModuleKind.FRONTEND, explicitOnly)
    val kind =
      if (hasOwnRpcEvidence) SplitModeApiRestrictionsService.ModuleKind.FRONTEND
      else SplitModeApiRestrictionsService.ModuleKind.LITE_FRONTEND
    return ResolvedModuleKind(kind, ModuleKindReason.OwnDependency(evidence, isRpcMissing = !hasOwnRpcEvidence))
  }

  fun buildMixedReasoning(): ModuleKindReason {
    return ModuleKindReason.MixedDependencies(
      frontendEvidence = findEvidence(SplitModeApiRestrictionsService.ModuleKind.FRONTEND),
      backendEvidence = findEvidence(SplitModeApiRestrictionsService.ModuleKind.BACKEND),
    )
  }

  fun buildContainingPluginsReasoning(): ModuleKindReason {
    return ModuleKindReason.ContainingPlugins(
      containingPlugins.sortedBy { it.moduleName }.map { ContainingPluginKind(it.moduleName, it.descriptorName, it.pluginId, it.moduleKind.kind) }
    )
  }

  fun buildSharedReasoning(): ModuleKindReason {
    val dependencyNames = listOfNotNull(
      ownFacts.frontendEvidence?.name,
      ownFacts.backendEvidence?.name,
      ownFacts.monolithEvidence?.name,
      containingFacts.frontendEvidence?.name,
      containingFacts.backendEvidence?.name,
      containingFacts.monolithEvidence?.name,
    ).distinct()
    return ModuleKindReason.NoPlatformDependencies(analysisTarget, dependencyNames)
  }

  private fun findOwnEvidence(kind: SplitModeApiRestrictionsService.ModuleKind, explicitOnly: Boolean): KindEvidence? {
    val dependencyInfo = ownFacts.evidence(kind) ?: return null
    if (explicitOnly && !isExplicitDependency(kind, dependencyInfo.name)) {
      return null
    }
    return KindEvidence(kind, dependencyInfo)
  }

  private fun findEvidence(kind: SplitModeApiRestrictionsService.ModuleKind): KindEvidence? {
    val dependencyInfo = ownFacts.evidence(kind) ?: containingFacts.evidence(kind) ?: return null
    return KindEvidence(kind, dependencyInfo)
  }

  private fun isExplicitDependency(kind: SplitModeApiRestrictionsService.ModuleKind, dependencyName: String): Boolean {
    return when (kind) {
      SplitModeApiRestrictionsService.ModuleKind.FRONTEND -> isExplicitFrontendDependency(dependencyName)
      SplitModeApiRestrictionsService.ModuleKind.BACKEND -> isExplicitBackendDependency(dependencyName)
      SplitModeApiRestrictionsService.ModuleKind.MONOLITH -> isExplicitMonolithDependency(dependencyName)
      else -> false
    }
  }
}

private fun computeContainingPluginsKind(containingPlugins: List<ContainingPlugin>): SplitModeApiRestrictionsService.ModuleKind {
  if (containingPlugins.isEmpty()) {
    return SplitModeApiRestrictionsService.ModuleKind.SHARED
  }

  val kinds = containingPlugins.mapTo(LinkedHashSet()) { it.moduleKind.kind }
  return when {
    kinds.size == 1 -> kinds.single()
    kinds == setOf(SplitModeApiRestrictionsService.ModuleKind.FRONTEND, SplitModeApiRestrictionsService.ModuleKind.LITE_FRONTEND) ->
      SplitModeApiRestrictionsService.ModuleKind.FRONTEND
    else -> SplitModeApiRestrictionsService.ModuleKind.MIXED
  }
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
      steps = listOf(DependencyTraceStep.ContainingPlugin) + trace.steps,
    )
  }
}
