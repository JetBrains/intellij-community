// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.remotedev.analysis

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.runBlockingCancellable
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

@Service(Service.Level.APP)
@ApiStatus.Internal
class SplitModeQodanaInspectionScopeLimiter(private val coroutineScope: CoroutineScope) {

  companion object {
    private val LOG: Logger = logger<SplitModeQodanaInspectionScopeLimiter>()
    private const val QODANA_ANALYSIS_SCOPE_FILE_PATH = "/remotedevInspectionData/SplitModeQodanaAnalysisScope.json"

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
    if (!ensureScopeIsLoaded()) {
      return false
    }

    val moduleNames = state.get().moduleNames
    if (moduleNames.isEmpty()) {
      return true
    }

    val module = ModuleUtilCore.findModuleForPsiElement(file) ?: return false
    return module.name in moduleNames
  }

  @TestOnly
  fun reloadScopeForTest() {
    val moduleNames = loadModuleNames()
    state.set(ScopeState(LoadingState.COMPLETED, moduleNames))
  }

  private fun isLoaded(): Boolean {
    return state.get().loadingState == LoadingState.COMPLETED
  }

  private fun ensureScopeIsLoaded(): Boolean {
    if (isLoaded()) {
      return true
    }

    scheduleLoadScope()
    if (isLoaded()) {
      return true
    }

    val loadedInTime = runBlockingCancellable {
      withTimeoutOrNull(1.seconds) {
        while (!isLoaded()) {
          delay(10.milliseconds)
        }
        true
      }
    }
    return loadedInTime == true
  }

  private fun scheduleLoadScope() {
    while (true) {
      val currentState = state.get()
      if (currentState.loadingState != LoadingState.NOT_STARTED) {
        return
      }

      val loadingState = currentState.copy(loadingState = LoadingState.IN_PROGRESS)
      if (state.compareAndSet(currentState, loadingState)) {
        coroutineScope.launch {
          loadScope()
        }
        return
      }
    }
  }

  private suspend fun loadScope() {
    var scopeState = ScopeState(loadingState = LoadingState.COMPLETED)
    try {
      LOG.info("Loading split-mode Qodana analysis scope from $QODANA_ANALYSIS_SCOPE_FILE_PATH")
      val moduleNames = withContext(Dispatchers.IO) {
        loadModuleNames()
      }
      scopeState = ScopeState(LoadingState.COMPLETED, moduleNames)
      LOG.info("Loaded ${moduleNames.size} split-mode Qodana analysis scope module names")
    }
    catch (e: Exception) {
      LOG.error("Failed to load split-mode Qodana analysis scope", e)
    }
    finally {
      state.set(scopeState)
    }
  }

  private fun loadModuleNames(): Set<String> {
    val moduleNames = mutableSetOf<String>()

    val bundledJsonText = readBundledJson(QODANA_ANALYSIS_SCOPE_FILE_PATH)
    if (bundledJsonText != null) {
      moduleNames.addAll(parseModuleNames(bundledJsonText))
    }

    val additionalJsonText = readAdditionalQodanaAnalysisScopeJson()
    if (additionalJsonText != null) {
      moduleNames.addAll(parseModuleNames(additionalJsonText))
    }

    return moduleNames
  }

  private fun parseModuleNames(jsonText: String): List<String> {
    return json.decodeFromString<SplitModeQodanaAnalysisScope>(jsonText).moduleNames
      .map { it.trim() }
      .filter { it.isNotEmpty() }
  }

  private fun readBundledJson(filePath: String): String? {
    val inputStream = SplitModeQodanaInspectionScopeLimiter::class.java.getResourceAsStream(filePath)
    if (inputStream == null) {
      LOG.warn("Split-mode Qodana analysis scope file not found: $filePath")
      return null
    }

    return inputStream.bufferedReader().use { it.readText() }
  }

  private fun readAdditionalQodanaAnalysisScopeJson(): String? {
    val filePath = SplitModeAnalysisFlags.getAdditionalQodanaAnalysisScopeFilePath() ?: return null
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
    val moduleNames: Set<String> = emptySet(),
  )

  @Serializable
  private data class SplitModeQodanaAnalysisScope(
    @SerialName("moduleNames")
    val moduleNames: List<String> = emptyList(),
  )
}
