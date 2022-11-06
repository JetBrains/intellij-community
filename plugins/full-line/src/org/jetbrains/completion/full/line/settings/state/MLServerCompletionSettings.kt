package org.jetbrains.completion.full.line.settings.state

import com.intellij.lang.Language
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.completion.full.line.language.*
import org.jetbrains.completion.full.line.models.ModelType

@State(
  name = "MLServerCompletionSettings",
  storages = [Storage("MLServerCompletionSettings.xml", deprecated = true), Storage("full.line.xml")]
)
class MLServerCompletionSettings : PersistentStateComponent<GeneralState> {
  private var state = GeneralState()

  // ----------------------------- Methods for state persisting ------------------------------ //
  override fun initializeComponent() {
    loadEvaluationConfig()
  }

  override fun loadState(state: GeneralState) {
    this.state.langStates.forEach {
      state.langStates.putIfAbsent(it.key, it.value)
    }
    this.state = state
  }

  companion object {
    fun isExtended() = Registry.get("full.line.expand.settings").asBoolean()

    fun getInstance(): MLServerCompletionSettings {
      return service()
    }

    val availableLanguages: List<Language>
      get() = FullLineLanguageSupporter.supportedLanguages()
  }

  // ----------------------------------- State getters --------------------------------------- //
  override fun getState() = state

  fun getLangState(language: Language): LangState = getLangStateSafe(language)
                                                    ?: throw IllegalArgumentException("Language ${language.displayName} is not supported")

  private fun getLangStateSafe(language: Language): LangState? = state.langStates[language.id]
                                                                 ?: language.baseLanguage?.let { state.langStates[it.id] }

  // ------------------------------ Common settings getters ---------------------------------- //

  fun isEnabled(): Boolean = state.enable

  fun isGreyTextMode(): Boolean = state.useGrayText

  fun enableStringsWalking(language: Language): Boolean = getLangState(language).stringsWalking

  fun showScore(language: Language): Boolean = getLangState(language).showScore

  fun topN(): Int? = if (state.useTopN) state.topN else null

  fun groupTopN(language: Language): Int? = getModelState(language).let { if (it.useGroupTopN) it.groupTopN else null }

  fun checkRedCode(language: Language): Boolean = getLangState(language).redCodePolicy != RedCodePolicy.SHOW

  fun hideRedCode(language: Language): Boolean = getLangState(language).redCodePolicy == RedCodePolicy.FILTER

  @Suppress("REDUNDANT_ELSE_IN_WHEN")
  fun getModelState(langState: LangState, type: ModelType = state.modelType): ModelState {
    return when (type) {
      ModelType.Cloud -> langState.cloudModelState
      ModelType.Local -> langState.localModelState
      else -> throw IllegalArgumentException("Got unexpected modelType `${type}`.")
    }
  }

  fun getModelState(language: Language, modelType: ModelType): ModelState = getModelState(getLangState(language), modelType)

  fun getModelMode(): ModelType = state.modelType

  fun getModelState(language: Language): ModelState = getModelState(getLangState(language))

  // --------------------------- Language-specific settings getters -------------------------- //

  fun isLanguageSupported(language: Language): Boolean = getLangStateSafe(language) != null

  // Check if completion enabled for current language or if the current language is based on supported
  fun isEnabled(language: Language): Boolean {
    // TODO: add correct handle for not specified language. Hotfix in 0.2.1
    return try {
      getLangState(language).enabled && isEnabled()
    }
    catch (e: Exception) {
      false
    }
  }

  private fun loadEvaluationConfig() {
    val isTesting = System.getenv("flcc_evaluating") ?: return
    if (!isTesting.toBoolean()) return

    // General settings
    System.getenv("flcc_enable")?.toBoolean()?.let { state.enable = it }
    System.getenv("flcc_topN")?.toInt()?.let {
      state.useTopN = true
      state.topN = it
    }
    System.getenv("flcc_useGrayText")?.toBoolean()?.let { state.useGrayText = it }
    System.getenv("flcc_modelType")?.let { state.modelType = ModelType.valueOf(it) }

    // Registries
    System.getenv("flcc_max_latency")?.let {
      Registry.get("full.line.server.host.max.latency").setValue(it.toInt())
    }
    System.getenv("flcc_only_proposals")?.let {
      Registry.get("full.line.only.proposals").setValue(it.toBoolean())
    }
    System.getenv("flcc_multi_token_only")?.let {
      Registry.get("full.line.multi.token.everywhere").setValue(it.toBoolean())
    }
    System.getenv("flcc_caching")?.let {
      Registry.get("full.line.enable.caching").setValue(it.toBoolean())
    }


    // Language-specific settings
    val langId = System.getenv("flcc_language") ?: return
    val langState = state.langStates
      .mapKeys { (k, _) -> k.toLowerCase() }
      .getValue(langId)
    System.getenv("flcc_enabled")?.toBoolean()?.let { langState.enabled = it }

    System.getenv("flcc_onlyFullLines")?.toBoolean()?.let { langState.onlyFullLines = it }
    System.getenv("flcc_stringsWalking")?.toBoolean()?.let { langState.stringsWalking = it }
    System.getenv("flcc_showScore")?.toBoolean()?.let { langState.showScore = it }
    System.getenv("flcc_redCodePolicy")?.let { langState.redCodePolicy = RedCodePolicy.valueOf(it) }
    System.getenv("flcc_groupAnswers")?.toBoolean()?.let { langState.groupAnswers = it }

    // Lang-registries
    @Suppress("UnresolvedPluginConfigReference") System.getenv("flcc_host")?.let {
      Registry.get("full.line.server.host.${langId.toLowerCase()}").setValue(it)
      Registry.get("full.line.server.host.psi.${langId.toLowerCase()}").setValue(it)
    }


    // Beam search configuration
    val modelState = getModelState(langState)
    System.getenv("flcc_numIterations")?.toInt()?.let { modelState.numIterations = it }
    System.getenv("flcc_beamSize")?.toInt()?.let { modelState.beamSize = it }
    System.getenv("flcc_diversityGroups")?.toInt()?.let { modelState.diversityGroups = it }
    System.getenv("flcc_diversityStrength")?.toDouble()?.let { modelState.diversityStrength = it }

    System.getenv("flcc_lenPow")?.toDouble()?.let { modelState.lenPow = it }
    System.getenv("flcc_lenBase")?.toDouble()?.let { modelState.lenBase = it }

    System.getenv("flcc_groupTopN")?.toInt()?.let {
      modelState.useGroupTopN = true
      modelState.groupTopN = it
    }
    System.getenv("flcc_customContextLength")?.toInt()?.let {
      modelState.useCustomContextLength = true
      modelState.customContextLength = it
    }

    System.getenv("flcc_minimumPrefixDist")?.toDouble()?.let { modelState.minimumPrefixDist = it }
    System.getenv("flcc_minimumEditDist")?.toDouble()?.let { modelState.minimumEditDist = it }
    System.getenv("flcc_keepKinds")?.let {
      modelState.keepKinds = it.removePrefix("[").removeSuffix("]")
        .split(",").asSequence()
        .onEach { it.trim() }.filter { it.isNotBlank() }
        .map { KeepKind.valueOf(it) }.toMutableSet()
    }

    System.getenv("flcc_psiBased")?.toBoolean()?.let { modelState.psiBased = it }
  }
}
