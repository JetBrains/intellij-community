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
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

@Service(Service.Level.APP)
@ApiStatus.Internal
class SplitModeQodanaInspectionScopeLimiter {

  companion object {
    private val LOG: Logger = logger<SplitModeQodanaInspectionScopeLimiter>()
    private const val QODANA_ANALYSIS_SCOPE_PROJECT_RELATIVE_PATH =
      "community/plugins/devkit/devkit-core/resources/remotedevInspectionData/SplitModeQodanaAnalysisScope.json"

    @JvmStatic
    fun getInstance(): SplitModeQodanaInspectionScopeLimiter = service()
  }

  @Volatile
  private var cachedScope: CachedScope? = null

  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  fun shouldInspectFileInQodanaMode(file: PsiFile): Boolean {
    if (!isQodanaOrUnitTestMode() || !SplitModeAnalysisFlags.isQodanaAnalysisScopeLimiterEnabled()) {
      return true
    }

    val scope = getScope(file.project)
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
  fun reloadScopeForTest(project: Project) {
    cachedScope = loadScope(project.basePath, SplitModeAnalysisFlags.getAdditionalQodanaAnalysisScopeFilePath())
  }

  private fun getScope(project: Project): ScopeConfiguration {
    val projectBasePath = project.basePath
    val additionalFilePath = SplitModeAnalysisFlags.getAdditionalQodanaAnalysisScopeFilePath()
    val cached = cachedScope
    if (cached != null && cached.projectBasePath == projectBasePath && cached.additionalFilePath == additionalFilePath) {
      return cached.scopeConfiguration
    }

    val loadedScope = loadScopeWithTimeout(projectBasePath, additionalFilePath)
    cachedScope = loadedScope
    return loadedScope.scopeConfiguration
  }

  private fun loadScopeWithTimeout(projectBasePath: String?, additionalFilePath: String?): CachedScope {
    return runBlockingCancellable {
      withTimeoutOrNull(1.seconds) {
        withContext(Dispatchers.IO) {
          loadScope(projectBasePath, additionalFilePath)
        }
      }
    } ?: CachedScope(projectBasePath, additionalFilePath, ScopeConfiguration()).also {
      LOG.warn("Timed out loading split-mode Qodana analysis scope")
    }
  }

  private fun loadScope(projectBasePath: String?, additionalFilePath: String?): CachedScope {
    val scopeConfiguration = try {
      loadScopeConfiguration(projectBasePath, additionalFilePath)
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
    return CachedScope(projectBasePath, additionalFilePath, scopeConfiguration)
  }

  private fun loadScopeConfiguration(projectBasePath: String?, additionalFilePath: String?): ScopeConfiguration {
    return listOfNotNull(
      readProjectQodanaAnalysisScopeJson(projectBasePath),
      readAdditionalQodanaAnalysisScopeJson(additionalFilePath),
    ).map(::parseScopeConfiguration)
      .fold(ScopeConfiguration()) { result, scope ->
        ScopeConfiguration(
          moduleNames = result.moduleNames + scope.moduleNames,
          ignoredModules = result.ignoredModules + scope.ignoredModules,
        )
      }
  }

  private fun readProjectQodanaAnalysisScopeJson(projectBasePath: String?): String? {
    if (projectBasePath == null) {
      LOG.info("Cannot load split-mode Qodana analysis scope: project base path is unknown")
      return null
    }

    val path = Path.of(projectBasePath).resolve(QODANA_ANALYSIS_SCOPE_PROJECT_RELATIVE_PATH)
    if (!Files.isRegularFile(path)) {
      LOG.info("Project split-mode Qodana analysis scope file does not exist: $QODANA_ANALYSIS_SCOPE_PROJECT_RELATIVE_PATH")
      return null
    }

    LOG.info("Loading project split-mode Qodana analysis scope from $path")
    return Files.readString(path)
  }

  private fun readAdditionalQodanaAnalysisScopeJson(filePath: String?): String? {
    if (filePath == null) {
      return null
    }

    val path = Path.of(filePath)
    if (!Files.isRegularFile(path)) {
      LOG.info("Additional split-mode Qodana analysis scope file does not exist: $filePath")
      return null
    }

    LOG.info("Loading additional split-mode Qodana analysis scope from $filePath")
    return Files.readString(path)
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
    val projectBasePath: String?,
    val additionalFilePath: String?,
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
