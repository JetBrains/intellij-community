// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.remotedev.analysis

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// The class is intentionally not coupled to the exsiting RestrictionsService since it represents a temporary solution for qodana-based
// split mode compatibility testing. After the testing phase is complete, we likely will get rid of the service, enable the tests for the entire monorepo
@Service(Service.Level.APP)
@ApiStatus.Internal
class SplitModeQodanaInspectionScopeLimiter(private val coroutineScope: CoroutineScope) {

  companion object {
    private val LOG: Logger = logger<SplitModeQodanaInspectionScopeLimiter>()
    private const val QODANA_ANALYSIS_SCOPE_PROJECT_RELATIVE_PATH =
      "community/plugins/devkit/devkit-core/resources/remotedevInspectionData/SplitModeQodanaAnalysisScope.json"

    @JvmStatic
    fun getInstance(): SplitModeQodanaInspectionScopeLimiter = service()
  }

  private enum class LoadingState {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED
  }

  private val state = AtomicReference(ScopeState())

  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  fun shouldInspectFileInQodanaMode(file: PsiFile): Boolean {
    if (!SplitModeAnalysisFlags.isQodanaAnalysisScopeLimiterEnabled()) {
      return true
    }

    val request = createScopeLoadRequest(file.project)
    if (!ensureScopeIsLoaded(request)) {
      return false
    }

    val loadedState = state.get()
    if (loadedState.scopeKey != request.scopeKey) {
      return false
    }

    val moduleNames = loadedState.moduleNames
    if (moduleNames.isEmpty()) {
      return true
    }

    val module = ModuleUtilCore.findModuleForPsiElement(file) ?: return false
    return module.name in moduleNames
  }

  @TestOnly
  fun reloadScopeForTest(project: Project) {
    val request = createScopeLoadRequest(project)
    val moduleNames = loadModuleNames(request)
    state.set(ScopeState(LoadingState.COMPLETED, request.scopeKey, moduleNames))
  }

  private fun createScopeLoadRequest(project: Project): ScopeLoadRequest {
    return ScopeLoadRequest(
      projectRoot = project.guessProjectDir(),
      additionalFilePath = SplitModeAnalysisFlags.getAdditionalQodanaAnalysisScopeFilePath(),
    )
  }

  private fun isLoaded(scopeKey: ScopeKey): Boolean {
    val currentState = state.get()
    return currentState.loadingState == LoadingState.COMPLETED && currentState.scopeKey == scopeKey
  }

  private fun ensureScopeIsLoaded(request: ScopeLoadRequest): Boolean {
    if (isLoaded(request.scopeKey)) {
      return true
    }

    scheduleLoadScope(request)
    if (isLoaded(request.scopeKey)) {
      return true
    }

    val loadedInTime = runBlockingCancellable {
      withTimeoutOrNull(1.seconds) {
        while (!isLoaded(request.scopeKey)) {
          delay(10.milliseconds)
        }
        true
      }
    }
    return loadedInTime == true
  }

  private fun scheduleLoadScope(request: ScopeLoadRequest) {
    while (true) {
      val currentState = state.get()
      if (currentState.scopeKey == request.scopeKey && currentState.loadingState != LoadingState.NOT_STARTED) {
        return
      }

      val loadingState = ScopeState(LoadingState.IN_PROGRESS, request.scopeKey)
      if (state.compareAndSet(currentState, loadingState)) {
        coroutineScope.launch {
          loadScope(request)
        }
        return
      }
    }
  }

  private suspend fun loadScope(request: ScopeLoadRequest) {
    var scopeState = ScopeState(loadingState = LoadingState.COMPLETED, scopeKey = request.scopeKey)
    try {
      LOG.info("Loading split-mode Qodana analysis scope from project file $QODANA_ANALYSIS_SCOPE_PROJECT_RELATIVE_PATH")
      val moduleNames = withContext(Dispatchers.IO) {
        loadModuleNames(request)
      }
      scopeState = ScopeState(LoadingState.COMPLETED, request.scopeKey, moduleNames)
      LOG.info("Loaded ${moduleNames.size} split-mode Qodana analysis scope module names")
    }
    catch (e: Exception) {
      LOG.error("Failed to load split-mode Qodana analysis scope", e)
    }
    finally {
      finishLoading(request.scopeKey, scopeState)
    }
  }

  private fun finishLoading(scopeKey: ScopeKey, scopeState: ScopeState) {
    while (true) {
      val currentState = state.get()
      if (currentState.scopeKey != scopeKey || currentState.loadingState != LoadingState.IN_PROGRESS) {
        return
      }
      if (state.compareAndSet(currentState, scopeState)) {
        return
      }
    }
  }

  private fun loadModuleNames(request: ScopeLoadRequest): Set<String> {
    val externallyProvidedModules = readAdditionalQodanaAnalysisScopeJson(request.additionalFilePath)
    if (!externallyProvidedModules.isNullOrBlank()) {
      LOG.warn("Split-mode Qodana analysis scope is provided externally")
      return parseModuleNames(externallyProvidedModules)
    }

    val moduleNamesFromCurrentlyInspectedProject = readProjectQodanaAnalysisScopeJson(request.projectRoot)
    if (!moduleNamesFromCurrentlyInspectedProject.isNullOrBlank()) {
      LOG.warn("Split-mode Qodana analysis scope is taken from currently inspected project")
      return parseModuleNames(moduleNamesFromCurrentlyInspectedProject)
    }

    LOG.warn("Split-mode Qodana analysis scope is not provided")
    return emptySet()
  }

  private fun parseModuleNames(jsonText: String): Set<String> {
    return json.decodeFromString<SplitModeQodanaAnalysisScope>(jsonText)
      .moduleNames.asSequence()
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .toSet()
  }

  private fun readProjectQodanaAnalysisScopeJson(projectRoot: VirtualFile?): String? {
    if (projectRoot == null) {
      LOG.info("Cannot load split-mode Qodana analysis scope: project root is unknown")
      return null
    }

    val scopeFile = projectRoot.findFileByRelativePath(QODANA_ANALYSIS_SCOPE_PROJECT_RELATIVE_PATH)
    if (scopeFile == null) {
      LOG.info("Project split-mode Qodana analysis scope file does not exist: $QODANA_ANALYSIS_SCOPE_PROJECT_RELATIVE_PATH")
      return null
    }

    LOG.info("Loading project split-mode Qodana analysis scope from ${scopeFile.path}")
    return VfsUtilCore.loadText(scopeFile)
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
    return Files.newBufferedReader(path).use { it.readText() }
  }

  private data class ScopeState(
    val loadingState: LoadingState = LoadingState.NOT_STARTED,
    val scopeKey: ScopeKey? = null,
    val moduleNames: Set<String> = emptySet(),
  )

  private data class ScopeLoadRequest(
    val projectRoot: VirtualFile?,
    val additionalFilePath: String?,
  ) {
    val scopeKey: ScopeKey = ScopeKey(projectRoot?.url, additionalFilePath)
  }

  private data class ScopeKey(
    val projectRootUrl: String?,
    val additionalFilePath: String?,
  )

  @Serializable
  private data class SplitModeQodanaAnalysisScope(
    @SerialName("moduleNames")
    val moduleNames: List<String> = emptyList(),
  )
}
