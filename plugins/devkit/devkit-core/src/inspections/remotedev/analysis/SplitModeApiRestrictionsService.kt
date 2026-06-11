// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.remotedev.analysis

import com.intellij.codeInsight.intention.FileModifier.SafeTypeForPreview
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.xml.XmlFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly
import org.jetbrains.idea.devkit.dom.IdeaPlugin
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeInspectionReloadableResource
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeInspectionResourceReadMode
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeInspectionResourceReader
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.toUElementOfType
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class SplitModeApiRestrictionsService(
  project: Project,
  private val coroutineScope: CoroutineScope,
) {

  companion object {
    private val LOG: Logger = logger<SplitModeApiRestrictionsService>()
    private const val API_RESTRICTIONS_RESOURCE_PATH = "remotedevInspectionData/ApiRestrictions.json"
    private const val PREDEFINED_MODULE_KINDS_RESOURCE_PATH = "remotedevInspectionData/PredefinedModuleKinds.json"
    private const val BACKEND_API_ANNOTATION = "com.intellij.util.remdev.BackendApi"
    private const val FRONTEND_API_ANNOTATION = "com.intellij.util.remdev.FrontendApi"

    @JvmStatic
    fun getInstance(project: Project): SplitModeApiRestrictionsService = project.service()
  }

  @SafeTypeForPreview
  sealed class ModuleKind(val id: String) {
    open fun accepts(actual: ModuleKind): Boolean {
      require(actual !is Composite) { "Actual module kind must not be composite" }
      return actual == MIXED || actual == MONOLITH || this == actual
    }

    object FRONTEND : ModuleKind("frontend")
    object BACKEND : ModuleKind("backend")
    object MONOLITH : ModuleKind("monolith")
    object MIXED : ModuleKind("mixed")
    object SHARED : ModuleKind("shared")

    data class Composite(val moduleKinds: List<ModuleKind>) : ModuleKind(
      moduleKinds.joinToString(" or ") { it.id }
    ) {
      init {
        require(moduleKinds.isNotEmpty()) { "Composite module kind must contain at least one module kind" }
        require(moduleKinds.none { it is Composite || it == MIXED }) { "Composite module kind must contain only singular module kinds" }
      }

      override fun accepts(actual: ModuleKind): Boolean {
        return moduleKinds.any { it.accepts(actual) }
      }
    }

    companion object {
      fun compositeOf(moduleKinds: Collection<ModuleKind>): ModuleKind {
        val flattenedModuleKinds = mutableSetOf<ModuleKind>()
        for (moduleKind in moduleKinds) {
          when (moduleKind) {
            is Composite -> flattenedModuleKinds.addAll(moduleKind.moduleKinds)
            MIXED -> error("'mixed' is not supported inside composite module kinds")
            else -> flattenedModuleKinds.add(moduleKind)
          }
        }
        check(flattenedModuleKinds.isNotEmpty()) { "targetModules must not be empty" }
        return if (flattenedModuleKinds.size == 1) flattenedModuleKinds.single() else Composite(flattenedModuleKinds.toList())
      }
    }
  }

  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  private val resourceReader = SplitModeInspectionResourceReader.getInstance(project)
  private val backgroundLoadScheduled = AtomicBoolean(false)
  private val loadLock = Any()

  @Volatile
  private var loaded = false

  @Volatile
  private var restrictionsSnapshot = RestrictionsSnapshot()

  private var apiRestrictionsResource = createApiRestrictionsResource(SplitModeAnalysisFlags.getApiRestrictionsReadMode())
  private var predefinedModuleKindsResource = createPredefinedModuleKindsResource(SplitModeAnalysisFlags.getPredefinedModuleKindsReadMode())

  fun isLoaded(): Boolean {
    return loaded
  }

  fun ensureLoaded(timeout: Duration = 1.seconds): Boolean {
    val loadedInTime = runBlockingCancellable {
      loadRestrictions(timeout)
    }
    return loadedInTime == true
  }

  @TestOnly
  @Suppress("RAW_RUN_BLOCKING")
  fun ensureLoadedBlocking(timeout: Duration = 1.seconds): Boolean {
    val loadedInTime = runBlocking {
      loadRestrictions(timeout)
    }
    return loadedInTime == true
  }

  fun getCodeApiKind(apiName: String, apiOwner: PsiModifierListOwner?): ModuleKind? {
    return getAnnotatedApiKind(apiOwner) ?: getRestrictionsSnapshot().codeRestrictions[apiName]
  }

  fun getExtensionPointKind(extensionPointName: String): ModuleKind? {
    val restrictions = getRestrictionsSnapshot()
    val apiName = restrictions.extensionPointToApiName[extensionPointName] ?: return null
    return restrictions.codeRestrictions[apiName]
  }

  fun getCodeApiHint(apiName: String): @Nls String? {
    return getRestrictionsSnapshot().apiHints[apiName]
  }

  fun getExtensionPointHint(extensionPointName: String): @Nls String? {
    val restrictions = getRestrictionsSnapshot()
    val apiName = restrictions.extensionPointToApiName[extensionPointName] ?: return null
    return restrictions.apiHints[apiName]
  }

  fun getPredefinedDependencyKind(dependencyId: String): ModuleKind? {
    val predefinedModuleKinds = getRestrictionsSnapshot().predefinedModuleKinds
    val moduleKind = predefinedModuleKinds.moduleNames[dependencyId]
    if (moduleKind != null) {
      return moduleKind
    }
    return predefinedModuleKinds.pluginIds[dependencyId]
  }

  internal fun getPredefinedModuleKind(
    module: Module,
    descriptorFile: XmlFile? = null,
    ideaPlugin: IdeaPlugin? = null,
  ): PredefinedModuleKindMatch? {
    val lookup = getRestrictionsSnapshot().predefinedModuleKinds
    val descriptorRelativePath = if (descriptorFile == null) null else computeDescriptorRelativePath(module, descriptorFile)
    if (descriptorRelativePath != null) {
      val selector = DescriptorPathSelector(module.name, descriptorRelativePath)
      val moduleKind = lookup.descriptorPaths[selector]
      if (moduleKind != null) {
        return PredefinedModuleKindMatch(
          moduleKind = moduleKind,
          cacheKey = "descriptor|${module.name}|$descriptorRelativePath",
          reasoning = "Predefined module kind for descriptor '$descriptorRelativePath' in module '${module.name}'",
        )
      }
    }

    if (ideaPlugin != null) {
      val pluginSelectorIds = collectPluginSelectorIds(ideaPlugin)
      for (pluginSelectorId in pluginSelectorIds) {
        val moduleKind = lookup.pluginIds[pluginSelectorId]
        if (moduleKind != null) {
          return PredefinedModuleKindMatch(
            moduleKind = moduleKind,
            cacheKey = "plugin|$pluginSelectorId",
            reasoning = "Predefined module kind for plugin/module id '$pluginSelectorId'",
          )
        }
      }
    }

    val moduleKind = lookup.moduleNames[module.name] ?: return null
    return PredefinedModuleKindMatch(
      moduleKind = moduleKind,
      cacheKey = "module|${module.name}",
      reasoning = "Predefined module kind for module '${module.name}'",
    )
  }

  @TestOnly
  fun assertApiRestrictionsCanBeReadForTest() {
    createApiRestrictionsResource(SplitModeAnalysisFlags.getApiRestrictionsReadMode()).getValue()
    createPredefinedModuleKindsResource(SplitModeAnalysisFlags.getPredefinedModuleKindsReadMode()).getValue()
  }

  @TestOnly
  fun reloadRestrictionsForTest() {
    apiRestrictionsResource = createApiRestrictionsResource(SplitModeAnalysisFlags.getApiRestrictionsReadMode())
    predefinedModuleKindsResource = createPredefinedModuleKindsResource(SplitModeAnalysisFlags.getPredefinedModuleKindsReadMode())
    publishRestrictionsSnapshot(buildRestrictionsSnapshot())
  }

  private suspend fun loadRestrictions(timeout: Duration): Boolean? {
    return withTimeoutOrNull(timeout) {
      withContext(Dispatchers.IO) {
        refreshRestrictionsSnapshot()
      }
    }
  }

  fun scheduleLoadRestrictions() {
    if (loaded || !backgroundLoadScheduled.compareAndSet(false, true)) {
      return
    }

    coroutineScope.launch {
      try {
        withContext(Dispatchers.IO) {
          refreshRestrictionsSnapshot()
        }
      }
      finally {
        backgroundLoadScheduled.set(false)
      }
    }
  }

  private fun getRestrictionsSnapshot(): RestrictionsSnapshot {
    return restrictionsSnapshot
  }

  private fun refreshRestrictionsSnapshot(): Boolean {
    synchronized(loadLock) {
      val previousSnapshot = restrictionsSnapshot
      val wasLoaded = loaded
      val newSnapshot = try {
        buildRestrictionsSnapshot()
      }
      catch (e: Exception) {
        LOG.error("Failed to load API restrictions", e)
        return loaded
      }

      publishRestrictionsSnapshot(newSnapshot)
      if (!wasLoaded || previousSnapshot != newSnapshot) {
        LOG.info(
          "Loaded ${newSnapshot.codeRestrictions.size} API restrictions, " +
          "${newSnapshot.extensionPointToApiName.size} extension point restrictions, " +
          "and ${newSnapshot.predefinedModuleKinds.size()} predefined module kinds"
        )
      }
      return true
    }
  }

  private fun buildRestrictionsSnapshot(): RestrictionsSnapshot {
    val apiRestrictions = apiRestrictionsResource.getValue()
    val predefinedModuleKinds = predefinedModuleKindsResource.getValue()
    return RestrictionsSnapshot(
      codeRestrictions = apiRestrictions.codeRestrictions,
      extensionPointToApiName = apiRestrictions.extensionPointToApiName,
      apiHints = apiRestrictions.apiHints,
      predefinedModuleKinds = predefinedModuleKinds,
    )
  }

  private fun publishRestrictionsSnapshot(snapshot: RestrictionsSnapshot) {
    restrictionsSnapshot = snapshot
    loaded = true
  }

  private fun createApiRestrictionsResource(readMode: SplitModeInspectionResourceReadMode): SplitModeInspectionReloadableResource<RestrictionsLookup> {
    return object : SplitModeInspectionReloadableResource<RestrictionsLookup>(
      resourceReader = resourceReader,
      resourcePath = API_RESTRICTIONS_RESOURCE_PATH,
      readMode = readMode,
    ) {
      override fun parse(text: String): RestrictionsLookup {
        return buildApiRestrictionsLookup(json.decodeFromString<List<ApiRestriction>>(text))
      }

      override fun getDefaultValue(): RestrictionsLookup {
        return RestrictionsLookup()
      }
    }
  }

  private fun createPredefinedModuleKindsResource(
    readMode: SplitModeInspectionResourceReadMode,
  ): SplitModeInspectionReloadableResource<PredefinedModuleKindsLookup> {
    return object : SplitModeInspectionReloadableResource<PredefinedModuleKindsLookup>(
      resourceReader = resourceReader,
      resourcePath = PREDEFINED_MODULE_KINDS_RESOURCE_PATH,
      readMode = readMode,
    ) {
      override fun parse(text: String): PredefinedModuleKindsLookup {
        return buildPredefinedModuleKindsLookup(json.decodeFromString<List<PredefinedModuleKind>>(text))
      }

      override fun getDefaultValue(): PredefinedModuleKindsLookup {
        return PredefinedModuleKindsLookup()
      }
    }
  }

  private fun buildApiRestrictionsLookup(data: List<ApiRestriction>): RestrictionsLookup {
    val codeRestrictions = mutableMapOf<String, ModuleKind>()
    val extensionPointToApiName = mutableMapOf<String, String>()
    val apiHints = mutableMapOf<String, String>()

    for (restriction in data) {
      val targetModuleKind = toTargetModuleKind(restriction)
      val existingCodeRestriction = codeRestrictions.putIfAbsent(restriction.apiName, targetModuleKind)
      check(existingCodeRestriction == null) {
        "Duplicate API restriction for '${restriction.apiName}'"
      }
      if (!restriction.hint.isNullOrBlank()) {
        apiHints[restriction.apiName] = restriction.hint
      }
      for (extensionPointName in restriction.extensionPointNames) {
        val existingApiName = extensionPointToApiName[extensionPointName]
        val existingExtensionPointRestriction = if (existingApiName == null) null else codeRestrictions[existingApiName]
        check(existingExtensionPointRestriction == null || existingExtensionPointRestriction == targetModuleKind) {
          "Conflicting extension point restriction for '$extensionPointName'"
        }
        if (existingApiName == null) {
          extensionPointToApiName[extensionPointName] = restriction.apiName
        }
      }
    }

    return RestrictionsLookup(
      codeRestrictions = codeRestrictions,
      extensionPointToApiName = extensionPointToApiName,
      apiHints = apiHints,
    )
  }

  private fun buildPredefinedModuleKindsLookup(data: List<PredefinedModuleKind>): PredefinedModuleKindsLookup {
    val moduleNames = mutableMapOf<String, ModuleKind>()
    val pluginIds = mutableMapOf<String, ModuleKind>()
    val descriptorPaths = mutableMapOf<DescriptorPathSelector, ModuleKind>()

    for ((moduleName, pluginId, descriptorRelativePath, moduleKindId) in data) {
      val moduleKind = parsePredefinedModuleKind(moduleKindId)
      val hasModuleName = !moduleName.isNullOrBlank()
      val hasPluginId = !pluginId.isNullOrBlank()

      check(hasModuleName != hasPluginId) {
        "Predefined module kind must specify exactly one of moduleName or pluginId"
      }
      check(descriptorRelativePath == null || hasModuleName) {
        "descriptorRelativePath is only supported together with moduleName"
      }
      check(descriptorRelativePath == null || descriptorRelativePath.isNotBlank()) {
        "descriptorRelativePath must not be blank"
      }

      if (descriptorRelativePath != null) {
        val selector = DescriptorPathSelector(moduleName!!, descriptorRelativePath)
        val previousModuleKind = descriptorPaths.putIfAbsent(selector, moduleKind)
        check(previousModuleKind == null) {
          "Duplicate predefined module kind for descriptor '${selector.descriptorRelativePath}' in module '${selector.moduleName}'"
        }
        continue
      }

      if (hasModuleName) {
        val previousModuleKind = moduleNames.putIfAbsent(moduleName, moduleKind)
        check(previousModuleKind == null) {
          "Duplicate predefined module kind for module '$moduleName'"
        }
      }
      else {
        val previousModuleKind = pluginIds.putIfAbsent(pluginId!!, moduleKind)
        check(previousModuleKind == null) {
          "Duplicate predefined module kind for plugin/module id '$pluginId'"
        }
      }
    }

    return PredefinedModuleKindsLookup(
      moduleNames = moduleNames,
      pluginIds = pluginIds,
      descriptorPaths = descriptorPaths,
    )
  }

  private fun getAnnotatedApiKind(apiOwner: PsiModifierListOwner?): ModuleKind? {
    val uAnnotated = apiOwner?.toUElementOfType<UAnnotated>() ?: return null
    val isFrontendApi = uAnnotated.findAnnotation(FRONTEND_API_ANNOTATION) != null
    val isBackendApi = uAnnotated.findAnnotation(BACKEND_API_ANNOTATION) != null

    return when {
      isFrontendApi && isBackendApi -> ModuleKind.compositeOf(listOf(ModuleKind.FRONTEND, ModuleKind.BACKEND))
      isFrontendApi -> ModuleKind.FRONTEND
      isBackendApi -> ModuleKind.BACKEND
      else -> null
    }
  }

  private fun toTargetModuleKind(apiRestriction: ApiRestriction): ModuleKind {
    return ModuleKind.compositeOf(apiRestriction.targetModules.map(::parseApiTargetModuleKind))
  }

  private fun parseApiTargetModuleKind(moduleKindId: String): ModuleKind {
    val moduleKind = parseModuleKindId(moduleKindId)
    check(moduleKind != ModuleKind.MIXED) { "'mixed' is not supported in targetModules" }
    return moduleKind
  }

  private fun parsePredefinedModuleKind(moduleKindId: String): ModuleKind {
    val moduleKind = parseModuleKindId(moduleKindId)
    check(moduleKind != ModuleKind.MIXED) { "'mixed' is not supported in predefined module kinds" }
    return moduleKind
  }

  private fun parseModuleKindId(moduleKindId: String): ModuleKind {
    return when (moduleKindId) {
      ModuleKind.FRONTEND.id -> ModuleKind.FRONTEND
      ModuleKind.BACKEND.id -> ModuleKind.BACKEND
      ModuleKind.MONOLITH.id -> ModuleKind.MONOLITH
      ModuleKind.SHARED.id -> ModuleKind.SHARED
      ModuleKind.MIXED.id -> ModuleKind.MIXED
      else -> error("Unknown split-mode module kind '$moduleKindId'")
    }
  }

  private fun collectPluginSelectorIds(ideaPlugin: IdeaPlugin): Sequence<String> {
    return sequence {
      val pluginId = ideaPlugin.pluginId
      if (!pluginId.isNullOrBlank()) {
        yield(pluginId)
      }

      for (pluginModule in ideaPlugin.modules) {
        val moduleValue = pluginModule.value.stringValue
        if (!moduleValue.isNullOrBlank()) {
          yield(moduleValue)
        }
      }
    }
      .distinct()
  }

  private fun computeDescriptorRelativePath(module: Module, descriptorFile: XmlFile): String? {
    val virtualFile = descriptorFile.virtualFile ?: return null
    val contentRoot = ProjectFileIndex.getInstance(module.project).getContentRootForFile(virtualFile) ?: return null
    return VfsUtilCore.getRelativePath(virtualFile, contentRoot, '/')
  }

  private data class RestrictionsLookup(
    val codeRestrictions: Map<String, ModuleKind> = emptyMap(),
    val extensionPointToApiName: Map<String, String> = emptyMap(),
    val apiHints: Map<String, String> = emptyMap(),
  )

  private data class RestrictionsSnapshot(
    val codeRestrictions: Map<String, ModuleKind> = emptyMap(),
    val extensionPointToApiName: Map<String, String> = emptyMap(),
    val apiHints: Map<String, String> = emptyMap(),
    val predefinedModuleKinds: PredefinedModuleKindsLookup = PredefinedModuleKindsLookup(),
  )

  internal data class PredefinedModuleKindMatch(
    val moduleKind: ModuleKind,
    val cacheKey: String,
    val reasoning: @NlsSafe String,
  )

  private data class DescriptorPathSelector(
    val moduleName: String,
    val descriptorRelativePath: String,
  )

  private data class PredefinedModuleKindsLookup(
    val moduleNames: Map<String, ModuleKind> = emptyMap(),
    val pluginIds: Map<String, ModuleKind> = emptyMap(),
    val descriptorPaths: Map<DescriptorPathSelector, ModuleKind> = emptyMap(),
  ) {
    fun size(): Int {
      return moduleNames.size + pluginIds.size + descriptorPaths.size
    }
  }

  @Serializable
  private data class ApiRestriction(
    @SerialName("apiName")
    val apiName: String,

    @SerialName("extensionPointNames")
    val extensionPointNames: List<String> = emptyList(),

    @SerialName("targetModules")
    val targetModules: List<String>,

    @SerialName("hint")
    val hint: @Nls String? = null,
  )

  @Serializable
  private data class PredefinedModuleKind(
    @SerialName("moduleName")
    val moduleName: String? = null,

    @SerialName("pluginId")
    val pluginId: String? = null,

    @SerialName("descriptorRelativePath")
    val descriptorRelativePath: String? = null,

    @SerialName("moduleKind")
    val moduleKind: String,
  )
}
