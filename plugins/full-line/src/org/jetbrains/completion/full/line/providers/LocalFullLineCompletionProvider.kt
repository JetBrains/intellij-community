package org.jetbrains.completion.full.line.providers

import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.ProjectManager
import io.kinference.model.ExecutionContext
import kotlinx.coroutines.Dispatchers
import org.jetbrains.completion.full.line.FullLineCompletionMode
import org.jetbrains.completion.full.line.FullLineProposal
import org.jetbrains.completion.full.line.RawFullLineProposal
import org.jetbrains.completion.full.line.currentOpenProject
import org.jetbrains.completion.full.line.language.ModelState
import org.jetbrains.completion.full.line.local.CompletionException
import org.jetbrains.completion.full.line.local.pipeline.FullLineCompletionPipelineConfig
import org.jetbrains.completion.full.line.models.CachingLocalPipeline
import org.jetbrains.completion.full.line.platform.FullLineCompletionQuery
import org.jetbrains.completion.full.line.platform.diagnostics.FullLinePart
import org.jetbrains.completion.full.line.platform.diagnostics.logger
import org.jetbrains.completion.full.line.services.LocalModelsCache
import org.jetbrains.completion.full.line.services.managers.ConfigurableModelsManager
import org.jetbrains.completion.full.line.settings.FullLineNotifications
import org.jetbrains.completion.full.line.settings.state.MLServerCompletionSettings


class LocalFullLineCompletionProvider private constructor(
  private val model: CachingLocalPipeline,
  private val config: ModelConfig
) : FullLineCompletionProvider {

  override fun getId(): String = "local"

  override fun getVariants(query: FullLineCompletionQuery, indicator: ProgressIndicator): List<RawFullLineProposal> {
    return try {
      val modelState = config.modelConfig
      val rawCompletions = model.generateCompletions(
        query.context,
        query.prefix,
        FullLineCompletionPipelineConfig(
          maxLen = modelState.numIterations,
          numBeams = modelState.beamSize,
          lenNormBase = modelState.lenBase,
          lenNormPow = modelState.lenPow,
          maxContextLen = modelState.contextLength(),
          filename = query.filename,
          oneTokenMode = query.mode == FullLineCompletionMode.ONE_TOKEN,
          numSuggestions = config.proposalsLimit,
        ),
        ExecutionContext(Dispatchers.Main, indicator::checkCanceled) // TODO: move to full.line.local
      )
      rawCompletions.mapNotNull {
        if (it.fullLineCompletionResult.text.trim().isNotEmpty()) {
          val proposal = RawFullLineProposal(
            it.fullLineCompletionResult.text.trimStart(),
            it.fullLineCompletionResult.info.probs.reduce(Double::times),
            FullLineProposal.BasicSyntaxCorrectness.UNKNOWN
          )
          proposal.details.cacheHitLength = it.cacheHitLength
          proposal
        }
        else null
      }
    }
    catch (exception: CompletionException) {
      LOG.debug(exception.message ?: "no-msg")
      emptyList()
    }
  }

  companion object {
    fun create(language: Language): FullLineCompletionProvider? {
      if (!checkedLanguages.contains(language.id)) {
        ApplicationManager.getApplication().executeOnPooledThread {
          service<ConfigurableModelsManager>().run {
            val project = ProjectManager.getInstance().currentOpenProject() ?: return@run
            getLatest(language, true).also {
              checkedLanguages.add(language.id)
              val cur = modelsSchema.targetLanguage(language)
              if (cur?.version != it.version) {
                FullLineNotifications.Local.showAvailableModelUpdate(project, language)
              }
            }
          }
        }
      }

      val model = LocalModelsCache.getInstance().tryGetModel(language) ?: return null
      val settings = MLServerCompletionSettings.getInstance()
      val localModelSettings = settings.getLangState(language).localModelState.copy()
      return LocalFullLineCompletionProvider(model, ModelConfig(settings.topN(), localModelSettings))
    }

    private val checkedLanguages = mutableSetOf<String>()

    private val LOG = logger<LocalFullLineCompletionProvider>(FullLinePart.NETWORK)
  }

  private data class ModelConfig(val proposalsLimit: Int?, val modelConfig: ModelState)
}
