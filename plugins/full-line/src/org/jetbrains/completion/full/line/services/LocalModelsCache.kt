package org.jetbrains.completion.full.line.services

import com.intellij.lang.Language
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.completion.full.line.currentOpenProject
import org.jetbrains.completion.full.line.models.CachingLocalPipeline
import org.jetbrains.completion.full.line.platform.diagnostics.DiagnosticsService
import org.jetbrains.completion.full.line.platform.diagnostics.FullLinePart
import org.jetbrains.completion.full.line.services.managers.ConfigurableModelsManager
import org.jetbrains.completion.full.line.settings.FullLineNotifications
import java.util.concurrent.ConcurrentHashMap

private val LOG = logger<LocalModelsCache>()

@Service
class LocalModelsCache {

  private val models: MutableMap<String, CachingLocalPipeline> = ConcurrentHashMap()

  fun tryGetModel(language: Language): CachingLocalPipeline? {
    val languageId = language.id
    if (languageId !in models) {
      scheduleModelLoad(languageId)
    }
    return models[languageId]
  }

  fun invalidate() {
    models.clear()
  }

  private fun scheduleModelLoad(languageId: String) {
    val startTimestamp = System.currentTimeMillis()
    val suitableModel = service<ConfigurableModelsManager>().modelsSchema.targetLanguage(languageId.toLowerCase())

    if (suitableModel != null) {
      MODELS_LOADER.submit {
        try {
          val tracer = DiagnosticsService.getInstance().logger(FullLinePart.BEAM_SEARCH, log = LOG)
          models[languageId] = suitableModel.loadModel { msg -> tracer.debug(msg) }
          LOG.info("Loading local model with key: \"$languageId\" took ${System.currentTimeMillis() - startTimestamp}ms.")
        }
        catch (e: Throwable) {
          LOG.error(e)
        }
      }
    }
    else {
      val project = ProjectManager.getInstanceIfCreated()?.currentOpenProject()
      if (project != null) {
        FullLineNotifications.Local.showMissingModel(project, Language.findLanguageByID(languageId)!!)
      }
    }
  }

  companion object {
    fun getInstance(): LocalModelsCache = service()
    val MODELS_LOADER = AppExecutorUtil.createBoundedApplicationPoolExecutor("Full Line local models loader", 1)
  }
}
