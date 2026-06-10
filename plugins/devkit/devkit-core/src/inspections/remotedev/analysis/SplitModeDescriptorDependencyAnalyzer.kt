// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.remotedev.analysis

import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.xml.XmlFile
import org.jetbrains.idea.devkit.dom.ContentDescriptor.ModuleDescriptor.ModuleLoadingRule
import org.jetbrains.idea.devkit.dom.Dependency
import org.jetbrains.idea.devkit.dom.IdeaPlugin
import org.jetbrains.idea.devkit.dom.index.PluginIdModuleIndex

private val OWN_DESCRIPTOR_DEPENDENCY_FACTS_KEY =
  Key.create<CachedValue<DependencyFacts>>("SplitModeDescriptorDependencyAnalyzer.ownDescriptorDependencyFacts")

internal sealed interface DescriptorDependencyFactsState {
  data object InProgress : DescriptorDependencyFactsState

  class Computed(val facts: DependencyFacts) : DescriptorDependencyFactsState
}

internal object SplitModeDescriptorDependencyAnalyzer {
  fun getOrComputeOwnDescriptorDependencyFacts(
    parsedXmlDescriptor: IdeaPlugin,
    analysisStates: MutableMap<XmlFile, DescriptorDependencyFactsState>,
  ): DependencyFacts {
    val descriptorFile = getDescriptorXmlFile(parsedXmlDescriptor)
    if (descriptorFile == null) {
      return computeOwnDescriptorDependencyFacts(parsedXmlDescriptor, analysisStates)
    }

    when (val state = analysisStates[descriptorFile]) {
      is DescriptorDependencyFactsState.Computed -> return state.facts
      DescriptorDependencyFactsState.InProgress -> return DependencyFacts()
      null -> {}
    }

    val cachedValue = descriptorFile.getUserData(OWN_DESCRIPTOR_DEPENDENCY_FACTS_KEY)
    if (cachedValue != null) {
      val cachedFactsReference = cachedValue.getUpToDateOrNull()
      if (cachedFactsReference != null) {
        val cachedFacts = cachedFactsReference.get()
        analysisStates[descriptorFile] = DescriptorDependencyFactsState.Computed(cachedFacts)
        return cachedFacts
      }
    }

    analysisStates[descriptorFile] = DescriptorDependencyFactsState.InProgress
    try {
      val dependencyFacts = computeOwnDescriptorDependencyFacts(parsedXmlDescriptor, analysisStates)
      analysisStates[descriptorFile] = DescriptorDependencyFactsState.Computed(dependencyFacts)
      cacheOwnDescriptorDependencyFacts(descriptorFile, dependencyFacts)
      return dependencyFacts
    }
    catch (t: Throwable) {
      analysisStates.remove(descriptorFile)
      throw t
    }
  }

  fun hasTransitiveDependency(ideaPlugin: IdeaPlugin, dependencyName: String): Boolean {
    val descriptorFile = getDescriptorXmlFile(ideaPlugin)
    val analysisStates = if (descriptorFile == null) {
      mutableMapOf()
    }
    else {
      mutableMapOf<XmlFile, DescriptorDependencyFactsState>(descriptorFile to DescriptorDependencyFactsState.InProgress)
    }
    val dependencyFacts = computeOwnDescriptorDependencyFacts(ideaPlugin, analysisStates)
    return dependencyFacts.representativeDependencies().any { it.name == dependencyName }
  }

  private fun computeOwnDescriptorDependencyFacts(
    ideaPlugin: IdeaPlugin,
    analysisStates: MutableMap<XmlFile, DescriptorDependencyFactsState>,
  ): DependencyFacts {
    val predefinedDependencyFacts = getPredefinedDependencyFacts(ideaPlugin)
    if (predefinedDependencyFacts != null) {
      return predefinedDependencyFacts
    }

    val descriptorLocation = getDescriptorLocation(ideaPlugin)
    val accumulator = DependencyFactsAccumulator(descriptorLocation)
    val directDependencies = collectDirectDependencies(ideaPlugin).toList()
    for ((dependencyName, dependencyDescriptors) in directDependencies) {
      var hasPredefinedDependencyFacts = false
      for (dependencyDescriptor in dependencyDescriptors) {
        val predefinedDependencyFacts = getPredefinedDependencyFacts(dependencyDescriptor) ?: continue
        accumulator.recordTransitiveDependencies(dependencyName, predefinedDependencyFacts)
        hasPredefinedDependencyFacts = true
      }
      if (!hasPredefinedDependencyFacts) {
        val predefinedDependencyFacts = getDirectPredefinedDependencyFacts(ideaPlugin, dependencyName, descriptorLocation)
        if (predefinedDependencyFacts != null) {
          accumulator.recordFacts(predefinedDependencyFacts)
          hasPredefinedDependencyFacts = true
        }
      }
      if (hasPredefinedDependencyFacts) {
        if (accumulator.hasMonolithEvidence()) {
          return accumulator.toDependencyFacts()
        }
      }
      else {
        accumulator.record(DependencyInfo(dependencyName, directDependencyTrace(descriptorLocation)))
        if (accumulator.hasMonolithEvidence()) {
          return accumulator.toDependencyFacts()
        }
      }
    }

    if (!SplitModeAnalysisFlags.isTransitiveDependenciesAnalysisEnabled()) {
      return accumulator.toDependencyFacts()
    }

    for ((dependencyName, dependencyDescriptors) in directDependencies) {
      for (dependencyDescriptor in dependencyDescriptors) {
        val dependencyFacts = getOrComputeOwnDescriptorDependencyFacts(dependencyDescriptor, analysisStates)
        accumulator.recordTransitiveDependencies(dependencyName, dependencyFacts)
        if (accumulator.hasMonolithEvidence()) {
          return accumulator.toDependencyFacts()
        }
      }
    }

    for (contentModule in collectEffectivelyLoadedContentModules(ideaPlugin)) {
      val contentDescriptor = contentModule.resolveDescriptor() ?: continue
      val dependencyFacts = getOrComputeOwnDescriptorDependencyFacts(contentDescriptor, analysisStates)
      accumulator.recordContentModuleDependencies(contentModule.loadingRule, dependencyFacts)
      if (accumulator.hasMonolithEvidence()) {
        return accumulator.toDependencyFacts()
      }
    }

    return accumulator.toDependencyFacts()
  }

  private fun cacheOwnDescriptorDependencyFacts(descriptorFile: XmlFile, dependencyFacts: DependencyFacts) {
    val project = descriptorFile.project
    descriptorFile.putUserData(
      OWN_DESCRIPTOR_DEPENDENCY_FACTS_KEY,
      CachedValuesManager.getManager(project).createCachedValue({
                                                                  CachedValueProvider.Result.create(
                                                                    dependencyFacts,
                                                                    ProjectRootModificationTracker.getInstance(project),
                                                                    PsiManager.getInstance(project).modificationTracker.forLanguage(
                                                                      XMLLanguage.INSTANCE),
                                                                  )
                                                                }, false)
    )
  }

  private fun collectDirectDependencies(ideaPlugin: IdeaPlugin): Sequence<DirectDependency> {
    return sequence {
      for (dependency in ideaPlugin.depends) {
        if (dependency.isOptionalOldStyleDependency()) {
          continue
        }
        val dependencyName = dependency.rawText ?: dependency.stringValue ?: continue
        yield(DirectDependency(dependencyName, resolvePluginDependencyDescriptors(ideaPlugin, dependencyName)))
      }

      val pluginDependencies = ideaPlugin.dependencies
      if (!pluginDependencies.isValid) {
        return@sequence
      }

      for (moduleDescriptor in pluginDependencies.moduleEntry) {
        val dependencyName = moduleDescriptor.name.stringValue ?: continue
        yield(DirectDependency(dependencyName, listOfNotNull(moduleDescriptor.name.value)))
      }

      for (pluginDescriptor in pluginDependencies.plugin) {
        val dependencyName = pluginDescriptor.id.stringValue ?: continue
        yield(DirectDependency(dependencyName, resolvePluginDependencyDescriptors(ideaPlugin, dependencyName)))
      }
    }
      .distinctBy { it.name }
      .sortedBy { it.name }
  }

  internal fun collectDirectDependencyNames(ideaPlugin: IdeaPlugin): Sequence<String> {
    return collectDirectDependencies(ideaPlugin).map { it.name }
  }

  private fun collectEffectivelyLoadedContentModules(ideaPlugin: IdeaPlugin): Sequence<ContentModuleDependency> {
    return sequence {
      for (contentDescriptor in ideaPlugin.content) {
        for (moduleDescriptor in contentDescriptor.moduleEntry) {
          val loadingRule = moduleDescriptor.loading.value
          if (loadingRule != ModuleLoadingRule.REQUIRED && loadingRule != ModuleLoadingRule.EMBEDDED) {
            continue
          }

          val moduleName = moduleDescriptor.name.stringValue ?: continue
          yield(ContentModuleDependency(moduleName, loadingRule) { moduleDescriptor.name.value })
        }
      }
    }
      .distinctBy { it.moduleName to it.loadingRule }
      .sortedWith(compareBy({ it.moduleName }, { it.loadingRule.value }))
  }
}

private fun Dependency.isOptionalOldStyleDependency(): Boolean = optional.value == true

private fun resolvePluginDependencyDescriptors(ideaPlugin: IdeaPlugin, dependencyName: String): List<IdeaPlugin> {
  val project = ideaPlugin.xmlElement?.project ?: return emptyList()
  val matchingPlugins = PluginIdModuleIndex.findPlugins(dependencyName, project)
    .distinctBy(::getDescriptorResolutionKey)
    .sortedBy(::getDescriptorPath)
  return matchingPlugins.filter { it.pluginId == dependencyName }.ifEmpty { matchingPlugins }
}

private fun getDirectPredefinedDependencyFacts(
  ideaPlugin: IdeaPlugin,
  dependencyName: String,
  descriptorLocation: DescriptorLocation,
): DependencyFacts? {
  val project = ideaPlugin.xmlElement?.project ?: return null
  val dependencyInfo = DependencyInfo(dependencyName, directDependencyTrace(descriptorLocation))
  return when (SplitModeApiRestrictionsService.getInstance(project).getPredefinedDependencyKind(dependencyName)) {
    SplitModeApiRestrictionsService.ModuleKind.FRONTEND -> DependencyFacts(frontendEvidence = dependencyInfo)
    SplitModeApiRestrictionsService.ModuleKind.BACKEND -> DependencyFacts(backendEvidence = dependencyInfo)
    SplitModeApiRestrictionsService.ModuleKind.MONOLITH -> DependencyFacts(monolithEvidence = dependencyInfo)
    else -> null
  }
}

private fun getPredefinedDependencyFacts(ideaPlugin: IdeaPlugin): DependencyFacts? {
  val descriptorFile = getDescriptorXmlFile(ideaPlugin) ?: return null
  val module = ModuleUtilCore.findModuleForPsiElement(descriptorFile) ?: return null
  val predefinedModuleKind =
    SplitModeApiRestrictionsService.getInstance(descriptorFile.project).getPredefinedModuleKind(module, descriptorFile, ideaPlugin)
    ?: return null
  return createPredefinedDependencyFacts(predefinedModuleKind)
}

private fun createPredefinedDependencyFacts(
  predefinedModuleKind: SplitModeApiRestrictionsService.PredefinedModuleKindMatch,
): DependencyFacts {
  return when (predefinedModuleKind.moduleKind) {
    SplitModeApiRestrictionsService.ModuleKind.FRONTEND -> DependencyFacts(
      frontendEvidence = createPredefinedDependencyInfo(SplitModeApiRestrictionsService.ModuleKind.FRONTEND, predefinedModuleKind),
    )
    SplitModeApiRestrictionsService.ModuleKind.BACKEND -> DependencyFacts(
      backendEvidence = createPredefinedDependencyInfo(SplitModeApiRestrictionsService.ModuleKind.BACKEND, predefinedModuleKind),
    )
    SplitModeApiRestrictionsService.ModuleKind.MONOLITH -> DependencyFacts(
      monolithEvidence = createPredefinedDependencyInfo(SplitModeApiRestrictionsService.ModuleKind.MONOLITH, predefinedModuleKind),
    )
    SplitModeApiRestrictionsService.ModuleKind.SHARED -> DependencyFacts()
    SplitModeApiRestrictionsService.ModuleKind.MIXED,
    is SplitModeApiRestrictionsService.ModuleKind.Composite,
      -> error("Unsupported predefined module kind: ${predefinedModuleKind.moduleKind}")
  }
}

private fun createPredefinedDependencyInfo(
  moduleKind: SplitModeApiRestrictionsService.ModuleKind,
  predefinedModuleKind: SplitModeApiRestrictionsService.PredefinedModuleKindMatch,
): DependencyInfo {
  return DependencyInfo(
    name = getExplicitPlatformDependencyName(moduleKind),
    trace = DependencyTrace(
      cacheKey = "predefined|${predefinedModuleKind.cacheKey}",
      description = predefinedModuleKind.reasoning,
    ),
  )
}

private class DependencyFactsAccumulator(
  private val descriptorLocation: DescriptorLocation,
) {
  private val seenDependencyNames = mutableSetOf<String>()
  private var frontendEvidence: DependencyInfo? = null
  private var backendEvidence: DependencyInfo? = null
  private var monolithEvidence: DependencyInfo? = null

  fun record(dependencyInfo: DependencyInfo) {
    val dependencyKind = when {
      isExplicitMonolithDependency(dependencyInfo.name) -> SplitModeApiRestrictionsService.ModuleKind.MONOLITH
      isFrontendDependency(dependencyInfo.name) -> SplitModeApiRestrictionsService.ModuleKind.FRONTEND
      isBackendDependency(dependencyInfo.name) -> SplitModeApiRestrictionsService.ModuleKind.BACKEND
      else -> null
    }
    record(dependencyInfo, dependencyKind)
  }

  fun recordFacts(dependencyFacts: DependencyFacts) {
    dependencyFacts.frontendEvidence?.let { record(it, SplitModeApiRestrictionsService.ModuleKind.FRONTEND) }
    dependencyFacts.backendEvidence?.let { record(it, SplitModeApiRestrictionsService.ModuleKind.BACKEND) }
    dependencyFacts.monolithEvidence?.let { record(it, SplitModeApiRestrictionsService.ModuleKind.MONOLITH) }
  }

  private fun record(
    dependencyInfo: DependencyInfo,
    dependencyKind: SplitModeApiRestrictionsService.ModuleKind?,
  ) {
    if (!seenDependencyNames.add(dependencyInfo.name)) {
      return
    }

    when (dependencyKind) {
      SplitModeApiRestrictionsService.ModuleKind.MONOLITH -> {
        if (monolithEvidence == null) {
          monolithEvidence = dependencyInfo
        }
      }
      SplitModeApiRestrictionsService.ModuleKind.FRONTEND -> {
        frontendEvidence = pickPreferredEvidence(frontendEvidence, dependencyInfo, ::isExplicitFrontendDependency)
      }
      SplitModeApiRestrictionsService.ModuleKind.BACKEND -> {
        backendEvidence = pickPreferredEvidence(backendEvidence, dependencyInfo, ::isExplicitBackendDependency)
      }
      null,
      SplitModeApiRestrictionsService.ModuleKind.MIXED,
      SplitModeApiRestrictionsService.ModuleKind.SHARED,
      is SplitModeApiRestrictionsService.ModuleKind.Composite,
        -> {
      }
    }
  }

  fun recordTransitiveDependencies(dependencyName: String, dependencyFacts: DependencyFacts) {
    for ((name, trace) in dependencyFacts.representativeDependencies()) {
      record(
        DependencyInfo(
          name,
          transitiveDependencyTrace(descriptorLocation, dependencyName, trace),
        )
      )
    }
  }

  fun recordContentModuleDependencies(
    loadingRule: ModuleLoadingRule,
    dependencyFacts: DependencyFacts,
  ) {
    for ((name, trace) in dependencyFacts.representativeDependencies()) {
      record(
        DependencyInfo(
          name,
          contentModuleDependencyTrace(loadingRule, trace),
        )
      )
    }
  }

  fun hasMonolithEvidence(): Boolean {
    return monolithEvidence != null
  }

  fun toDependencyFacts(): DependencyFacts {
    return DependencyFacts(frontendEvidence, backendEvidence, monolithEvidence)
  }

  private fun pickPreferredEvidence(
    current: DependencyInfo?,
    candidate: DependencyInfo,
    isExplicitDependency: (String) -> Boolean,
  ): DependencyInfo {
    if (current == null) {
      return candidate
    }

    return if (isExplicitDependency(candidate.name) && !isExplicitDependency(current.name)) {
      candidate
    }
    else {
      current
    }
  }
}

private data class DirectDependency(
  val name: String,
  val descriptors: List<IdeaPlugin>,
)

private data class ContentModuleDependency(
  val moduleName: String,
  val loadingRule: ModuleLoadingRule,
  private val resolver: () -> IdeaPlugin?,
) {
  fun resolveDescriptor(): IdeaPlugin? = resolver()
}

private data class DescriptorLocation(
  val descriptorName: String,
  val moduleName: String?,
) {
  fun presentableName(): String {
    return if (moduleName != null) {
      "descriptor '$descriptorName' in module '$moduleName'"
    }
    else {
      "descriptor '$descriptorName'"
    }
  }
}

private fun directDependencyTrace(descriptorLocation: DescriptorLocation): DependencyTrace {
  return DependencyTrace(
    cacheKey = "direct|${descriptorLocation.moduleName}|${descriptorLocation.descriptorName}",
    description = descriptorLocation.presentableName(),
  )
}

private fun transitiveDependencyTrace(
  descriptorLocation: DescriptorLocation,
  viaDependencyName: String,
  nestedTrace: DependencyTrace,
): DependencyTrace {
  val nestedDescription = nestedTrace.description
  return DependencyTrace(
    cacheKey = "transitive|${descriptorLocation.moduleName}|${descriptorLocation.descriptorName}|$viaDependencyName|${nestedTrace.cacheKey}",
    description = buildString {
      append(descriptorLocation.presentableName())
      append("\nvia dependency '")
      append(viaDependencyName)
      append("' -> ")
      append(nestedDescription)
      if (!nestedDescription.endsWith(".")) {
        append('.')
      }
    },
  )
}

private fun contentModuleDependencyTrace(
  loadingRule: ModuleLoadingRule,
  nestedTrace: DependencyTrace,
): DependencyTrace {
  return DependencyTrace(
    cacheKey = "content|${loadingRule.value}|${nestedTrace.cacheKey}",
    description = "${loadingRule.value} content module ${nestedTrace.description}",
  )
}

private fun getDescriptorLocation(ideaPlugin: IdeaPlugin): DescriptorLocation {
  val xmlFile = getDescriptorXmlFile(ideaPlugin)
  if (xmlFile == null) {
    return DescriptorLocation("unknown descriptor location", null)
  }

  val module = ModuleUtilCore.findModuleForPsiElement(xmlFile)
  return DescriptorLocation(xmlFile.name, module?.name)
}

private fun getDescriptorXmlFile(ideaPlugin: IdeaPlugin): XmlFile? {
  return ideaPlugin.xmlElement?.containingFile?.originalFile as? XmlFile
}

private fun getDescriptorPath(ideaPlugin: IdeaPlugin): String {
  return getDescriptorXmlFile(ideaPlugin)?.virtualFile?.path ?: ""
}

private fun getDescriptorResolutionKey(ideaPlugin: IdeaPlugin): String {
  return getDescriptorXmlFile(ideaPlugin)?.virtualFile?.path ?: ideaPlugin.pluginId ?: ""
}
