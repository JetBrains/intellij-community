// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.remotedev.analysis

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.PlatformUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeInspectionResourceReadMode
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeInspectionResourceReader
import kotlin.time.Duration.Companion.seconds

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class SplitModeQodanaInspectionScopeLimiter(private val project: Project) {

  companion object {
    private val LOG: Logger = logger<SplitModeQodanaInspectionScopeLimiter>()
    private const val QODANA_ANALYSIS_SCOPE_RESOURCE_PATH = "remotedevInspectionData/SplitModeQodanaAnalysisScope.json"

    @JvmStatic
    fun getInstance(project: Project): SplitModeQodanaInspectionScopeLimiter = project.service()
  }

  @Volatile
  private var cachedScope: CachedScope? = null

  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  private val resourceReader: SplitModeInspectionResourceReader
    get() = SplitModeInspectionResourceReader.getInstance(project)

  fun shouldInspectFileInQodanaMode(file: PsiFile): Boolean {
    if (!isQodanaOrUnitTestMode() || !SplitModeAnalysisFlags.isQodanaAnalysisScopeLimiterEnabled()) {
      return true
    }

    val scope = getScope()
    if (scope.isEmpty()) {
      return true
    }

    val module = ModuleUtilCore.findModuleForPsiElement(file) ?: return false
    return scope.shouldInspect(module.name)
  }

  private fun isQodanaOrUnitTestMode(): Boolean {
    return PlatformUtils.isQodana() || ApplicationManager.getApplication().isUnitTestMode
  }

  @TestOnly
  fun reloadScopeForTest() {
    cachedScope = loadScope(SplitModeAnalysisFlags.getQodanaAnalysisScopeReadMode())
  }

  private fun getScope(): ScopeConfiguration {
    val readMode = SplitModeAnalysisFlags.getQodanaAnalysisScopeReadMode()
    val cached = cachedScope
    if (cached != null && cached.readMode == readMode) {
      return cached.scopeConfiguration
    }

    val loadedScope = loadScopeWithTimeout(readMode)
    cachedScope = loadedScope
    return loadedScope.scopeConfiguration
  }

  private fun loadScopeWithTimeout(readMode: SplitModeInspectionResourceReadMode): CachedScope {
    return runBlockingCancellable {
      withTimeoutOrNull(1.seconds) {
        withContext(Dispatchers.IO) {
          loadScope(readMode)
        }
      }
    } ?: CachedScope(readMode, ScopeConfiguration()).also {
      LOG.warn("Timed out loading split-mode Qodana analysis scope")
    }
  }

  private fun loadScope(readMode: SplitModeInspectionResourceReadMode): CachedScope {
    val scopeConfiguration = try {
      loadScopeConfiguration(readMode)
    }
    catch (e: IllegalArgumentException) {
      LOG.warn("Ignoring invalid split-mode Qodana analysis scope configuration", e)
      ScopeConfiguration()
    }
    catch (e: Exception) {
      LOG.error("Failed to load split-mode Qodana analysis scope", e)
      ScopeConfiguration()
    }

    if (scopeConfiguration.moduleNames.isNotEmpty() && scopeConfiguration.ignoredModules.isNotEmpty()) {
      LOG.warn("Both moduleNames and ignoredModules are configured for split-mode Qodana analysis scope; using moduleNames")
    }

    LOG.info(
      "Loaded ${scopeConfiguration.moduleNames.size} split-mode Qodana analysis scope module names and " +
      "${scopeConfiguration.ignoredModules.size} ignored modules",
    )
    return CachedScope(readMode, scopeConfiguration)
  }

  private fun loadScopeConfiguration(readMode: SplitModeInspectionResourceReadMode): ScopeConfiguration {
    val jsonText = resourceReader.readText(QODANA_ANALYSIS_SCOPE_RESOURCE_PATH, readMode) ?: return ScopeConfiguration()
    return parseScopeConfiguration(jsonText)
  }

  private fun parseScopeConfiguration(jsonText: String): ScopeConfiguration {
    val scope = json.decodeFromString<SplitModeQodanaAnalysisScope>(jsonText)
    val moduleNames = parseConfiguredModuleNames(scope.moduleNames)
    val ignoredModules = parseConfiguredModuleNames(scope.ignoredModules)
    require(moduleNames.isEmpty() || ignoredModules.isEmpty()) {
      "Split-mode Qodana analysis scope must define either moduleNames or ignoredModules, but not both"
    }
    return ScopeConfiguration(moduleNames, ignoredModules)
  }

  private data class CachedScope(
    val readMode: SplitModeInspectionResourceReadMode,
    val scopeConfiguration: ScopeConfiguration,
  )

  private data class ScopeConfiguration(
    val moduleNames: Set<String> = emptySet(),
    val ignoredModules: Set<String> = emptySet(),
  ) {
    fun isEmpty(): Boolean {
      return moduleNames.isEmpty() && ignoredModules.isEmpty()
    }

    fun shouldInspect(moduleName: String): Boolean {
      return when {
        moduleNames.isNotEmpty() -> moduleName in moduleNames
        ignoredModules.isNotEmpty() -> moduleName !in ignoredModules
        else -> true
      }
    }
  }

  private fun parseConfiguredModuleNames(moduleNames: List<String>): Set<String> {
    return moduleNames.asSequence()
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .toSet()
  }

  @Serializable
  private data class SplitModeQodanaAnalysisScope(
    @SerialName("moduleNames")
    val moduleNames: List<String> = emptyList(),

    @SerialName("ignoredModules")
    val ignoredModules: List<String> = emptyList(),
  )
}
