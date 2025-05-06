package com.intellij.findUsagesMl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FileRankerMlService
import com.jetbrains.ml.api.feature.FeatureSet
import com.jetbrains.ml.tools.logs.MLLogsTree
import com.jetbrains.ml.tools.logs.MLTreeLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

@Service(Service.Level.APP)
class FileRankerMlServiceImpl(private val coroutineScope: CoroutineScope) : FileRankerMlService {
  companion object {
    private const val IS_ENABLED_REGISTRY_KEY = "find.usages.ml.ranker.isEnabled"
    private const val USE_OLD_IMPL_REGISTRY_KEY = "find.usages.ml.ranker.useOldImplementation"
  }

  private val featureProvider: FindUsagesFileRankerFeatureProvider = FindUsagesFileRankerFeatureProvider()
  private val analysisProvider: FindUsagesFileRankerAnalysisProvider = FindUsagesFileRankerAnalysisProvider()
  private val logger: MLTreeLogger = FindUsagesFileRankerFeatureCollector.mlLogger

  private val activeSessionCounter: AtomicLong = AtomicLong(0)
  private val finishedSessionCounter = AtomicLong(0)

  private val sessionData: AtomicReference<SearchSessionData> = AtomicReference(SearchSessionData(emptyList(),
                                                                                                   emptyList(),
                                                                                                   emptyList()))

  private fun clear() {
    sessionData.set(SearchSessionData(queryNames = emptyList(),
                                      queryFiles = emptyList(),
                                      candidateFiles = emptyList()))
  }

  override fun onSessionFinished(project: Project?,
                                 foundUsageFiles: Set<VirtualFile>,
                                 callSource: FileRankerMlService.CallSource) {
    val finishedSessionData = sessionData.get()
    clear()
    coroutineScope.launch {
      val sessionId = finishedSessionCounter.incrementAndGet()

      if (activeSessionCounter.get() != sessionId || finishedSessionData.queryNames.isEmpty()) {
        // CORRUPTED SESSION
        logInvalid(activeSessionCounter.get(), sessionId)

        val sessionFixVal = max(activeSessionCounter.get(), finishedSessionCounter.get()) // fix active session id to avoid duplicates
        activeSessionCounter.set(sessionFixVal)
        finishedSessionCounter.set(sessionFixVal)
      } else {
        // VALID SESSION
        val timeStamp = System.currentTimeMillis()
        val recentFilesList = getRecentFilesList(project)

        val queryFiles = finishedSessionData.queryFiles()
        finishedSessionData.candidateFiles().forEach { logFeatures(file = it,
                                                                   queryNames = finishedSessionData.queryNames,
                                                                   queryFiles = queryFiles,
                                                                   isUsage = foundUsageFiles.contains(it),
                                                                   recentFilesList = recentFilesList,
                                                                   timeStamp = timeStamp,
                                                                   sessionId = sessionId,
                                                                   isSearchValid = true) }
      }
    }
  }

  private fun logFeatures(file: VirtualFile?,
                          queryNames: List<String>,
                          queryFiles: List<VirtualFile>,
                          isUsage: Boolean,
                          recentFilesList: List<VirtualFile>,
                          timeStamp: Long,
                          sessionId: Long,
                          isSearchValid: Boolean,
                          activeSessionId: Long? = null) {
    val tree = MLLogsTree(
      analysis = analysisProvider.provideAnalysisTargets(
        info = FindUsagesFileRankingAnalysisInfo(isUsage = isUsage,
                                                 timestamp = timeStamp,
                                                 isSearchValid = isSearchValid,
                                                 activeSessionId = activeSessionId,
                                                 finishSessionId = sessionId)),
      features = featureProvider.provideFeatures(
        instance = FindUsagesRankingFileInfo(queryNames = queryNames,
                                             queryFiles = queryFiles,
                                             candidateFile = file,
                                             recentFilesList = recentFilesList,
                                             timeStamp = timeStamp),
        requiredOutput = FeatureSet.ALL
      ),
    )
    logger.log(tree = tree,
               customSessionId = sessionId)
  }

  private fun logInvalid(activeSessionId: Long, finishedSessionId: Long) {
    logFeatures(file = null,
                queryNames = emptyList(),
                queryFiles = emptyList(),
                isUsage = false,
                recentFilesList = emptyList(),
                timeStamp = System.currentTimeMillis(),
                sessionId = finishedSessionId,
                isSearchValid = false,
                activeSessionId = activeSessionId)
  }

  private fun setFiles(queryNames: List<String>,
                       queryFiles: List<VirtualFile>,
                       candidateFiles: List<VirtualFile>) {
    this.sessionData.set(SearchSessionData(queryNames,
                                           queryFiles,
                                           candidateFiles))
  }

  override fun getFileOrder(queryNames: List<String>,
                            queryFiles: List<VirtualFile>,
                            candidateFiles: List<VirtualFile>): List<VirtualFile> {
    activeSessionCounter.incrementAndGet()
    setFiles(queryNames, queryFiles, candidateFiles)
    return candidateFiles
  }

  override fun isEnabled(): Boolean {
    val app = ApplicationManager.getApplication()
    return !app.isUnitTestMode && app.isEAP &&
           Registry.`is`(IS_ENABLED_REGISTRY_KEY, false)
  }

  override fun shouldUseOldImplementation(): Boolean = Registry.`is`(USE_OLD_IMPL_REGISTRY_KEY, false)

  private fun getRecentFilesList(project: Project?): List<VirtualFile> {
    if (project == null) {
      return listOf()
    }
    return EditorHistoryManager.getInstance(project).fileList
  }

}
