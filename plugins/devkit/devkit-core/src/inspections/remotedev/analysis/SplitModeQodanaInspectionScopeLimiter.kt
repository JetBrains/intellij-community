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

    val moduleNames = getModuleNames(file.project)
    if (moduleNames.isEmpty()) {
      return true
    }

    val module = ModuleUtilCore.findModuleForPsiElement(file) ?: return false
    return module.name in moduleNames
  }

  private fun isQodanaOrUnitTestMode(): Boolean {
    return PlatformUtils.isQodana() || ApplicationManager.getApplication().isUnitTestMode
  }

  @TestOnly
  fun reloadScopeForTest(project: Project) {
    cachedScope = loadScope(project.basePath, SplitModeAnalysisFlags.getAdditionalQodanaAnalysisScopeFilePath())
  }

  private fun getModuleNames(project: Project): Set<String> {
    val projectBasePath = project.basePath
    val additionalFilePath = SplitModeAnalysisFlags.getAdditionalQodanaAnalysisScopeFilePath()
    val cached = cachedScope
    if (cached != null && cached.projectBasePath == projectBasePath && cached.additionalFilePath == additionalFilePath) {
      return cached.moduleNames
    }

    val loadedScope = loadScopeWithTimeout(projectBasePath, additionalFilePath)
    cachedScope = loadedScope
    return loadedScope.moduleNames
  }

  private fun loadScopeWithTimeout(projectBasePath: String?, additionalFilePath: String?): CachedScope {
    return runBlockingCancellable {
      withTimeoutOrNull(1.seconds) {
        withContext(Dispatchers.IO) {
          loadScope(projectBasePath, additionalFilePath)
        }
      }
    } ?: CachedScope(projectBasePath, additionalFilePath, emptySet()).also {
      LOG.warn("Timed out loading split-mode Qodana analysis scope")
    }
  }

  private fun loadScope(projectBasePath: String?, additionalFilePath: String?): CachedScope {
    val moduleNames = try {
      loadModuleNames(projectBasePath, additionalFilePath)
    }
    catch (e: Exception) {
      LOG.error("Failed to load split-mode Qodana analysis scope", e)
      emptySet()
    }
    LOG.info("Loaded ${moduleNames.size} split-mode Qodana analysis scope module names")
    return CachedScope(projectBasePath, additionalFilePath, moduleNames)
  }

  private fun loadModuleNames(projectBasePath: String?, additionalFilePath: String?): Set<String> {
    return listOfNotNull(
      readProjectQodanaAnalysisScopeJson(projectBasePath),
      readAdditionalQodanaAnalysisScopeJson(additionalFilePath),
    ).flatMap(::parseModuleNames).toSet()
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

  private fun parseModuleNames(jsonText: String): List<String> {
    return json.decodeFromString<SplitModeQodanaAnalysisScope>(jsonText).moduleNames
      .map { it.trim() }
      .filter { it.isNotEmpty() }
  }

  private data class CachedScope(
    val projectBasePath: String?,
    val additionalFilePath: String?,
    val moduleNames: Set<String>,
  )

  @Serializable
  private data class SplitModeQodanaAnalysisScope(
    @SerialName("moduleNames")
    val moduleNames: List<String> = emptyList(),
  )
}
