package org.jetbrains.completion.full.line.platform.logs

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.ml.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.lang.Language
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.completion.full.line.AnalyzedFullLineProposal
import org.jetbrains.completion.full.line.language.LangState
import org.jetbrains.completion.full.line.language.ModelState
import org.jetbrains.completion.full.line.platform.FullLineLookupElement
import org.jetbrains.completion.full.line.settings.state.MLServerCompletionSettings

private const val PROVIDERS_NAME = "full_line"
private val FULL_LINE_AVAILABLE_KEY = Key.create<Boolean>("fl.available")

class FullLineContextFeatureProvider : ContextFeatureProvider {
  override fun getName(): String = PROVIDERS_NAME

  override fun calculateFeatures(environment: CompletionEnvironment): Map<String, MLFeatureValue> {
    val language = environment.parameters.originalFile.language
    val settings = MLServerCompletionSettings.getInstance()
    if (!settings.isLanguageSupported(language)) return emptyMap()

    environment.putUserData(FULL_LINE_AVAILABLE_KEY, true)

    val result = mutableMapOf<String, MLFeatureValue>()
    settings.capture(result, language)

    return result
  }

  private fun MLServerCompletionSettings.capture(result: MutableMap<String, MLFeatureValue>, language: Language) {
    result["installed"] = MLFeatureValue.binary(true)
    val isEnabled = isEnabled(language)
    result["enabled"] = MLFeatureValue.binary(isEnabled)

    if (!isEnabled) return

    result["model_type"] = MLFeatureValue.categorical(getModelMode())

    getLangState(language).capture(result)
    getModelState(language).capture(result)

    if (Registry.`is`("full.line.only.proposals")) {
      result["hide_standard_proposals"] = MLFeatureValue.binary(true)
    }
  }

  private fun LangState.capture(result: MutableMap<String, MLFeatureValue>) {
    result["red_code_policy"] = MLFeatureValue.categorical(redCodePolicy)

    if (onlyFullLines) {
      result["full_lines_enabled"] = MLFeatureValue.binary(true)
    }

    result["strings_walking"] = MLFeatureValue.binary(stringsWalking)
  }

  private fun ModelState.capture(result: MutableMap<String, MLFeatureValue>) {
    result["num_iterations"] = MLFeatureValue.numerical(numIterations)
    result["beam_size"] = MLFeatureValue.numerical(beamSize)
  }
}

class FullLineElementFeatureProvider : ElementFeatureProvider {
  override fun getName(): String = PROVIDERS_NAME

  override fun calculateFeatures(element: LookupElement,
                                 location: CompletionLocation,
                                 contextFeatures: ContextFeatures): Map<String, MLFeatureValue> {
    if (contextFeatures.getUserData(FULL_LINE_AVAILABLE_KEY) != true) return emptyMap()
    val fullLineElement = element.asFullLineElement()
    return if (fullLineElement != null) calculateFullLineFeatures(fullLineElement) else emptyMap()
  }

  private fun calculateFullLineFeatures(element: FullLineLookupElement): Map<String, MLFeatureValue> {
    val features = mutableMapOf(
      "tab_selected" to MLFeatureValue.binary(element.selectedByTab),
    )

    features.putAll(fromProposal(element.proposal))
    return features
  }

  private fun fromProposal(proposal: AnalyzedFullLineProposal): List<Pair<String, MLFeatureValue>> {
    val details = proposal.details
    return listOfNotNull(
      "ref_validity" to MLFeatureValue.categorical(proposal.refCorrectness),
      "syntax_validity" to MLFeatureValue.categorical(proposal.isSyntaxCorrect),
      "suffix_length" to MLFeatureValue.float(proposal.suffix.length),
      "score" to MLFeatureValue.float(proposal.score),
      details.inferenceTime?.let { "inference_time" to MLFeatureValue.numerical(it) },
      details.checksTime?.let { "checks_time" to MLFeatureValue.numerical(it) },
      details.cacheHitLength?.let { "cache_hit_length" to MLFeatureValue.numerical(it) }
    )
  }

  private fun LookupElement.asFullLineElement(): FullLineLookupElement? {
    if (this is FullLineLookupElement) {
      return this
    }

    if (this is LookupElementDecorator<*>) {
      return delegate.asFullLineElement()
    }

    return null
  }
}