package org.jetbrains.completion.full.line.services.managers

import com.intellij.lang.Language
import org.jetbrains.completion.full.line.local.LocalModelsSchema
import org.jetbrains.completion.full.line.local.ModelSchema
import org.jetbrains.completion.full.line.models.CachingLocalPipeline
import org.jetbrains.completion.full.line.settings.state.MLServerCompletionSettings

interface ConfigurableModelsManager : ModelsManager {
  val modelsSchema: LocalModelsSchema

  fun importLocal(language: Language, modelPath: String): ModelSchema

  fun remove(language: Language)

  fun checkFile(language: Language): Boolean

  fun apply()

  fun reset()

  fun loadModel(model: ModelSchema, loggingCallback: ((String) -> Unit)? = null): CachingLocalPipeline
}

fun ConfigurableModelsManager.missedLanguage(language: Language): Boolean {
  return modelsSchema.targetLanguage(language) == null && MLServerCompletionSettings.getInstance().isEnabled(language)
}

//fun ConfigurableModelsManager.missedFiles(language: Language): Boolean {
//    return modelsSchema.targetLanguage(language)?.let {
//        !it.binaryFile().exists() || !it.bpeFile().exists() || !it.configFile().exists()
//    } ?: false
//}

fun ConfigurableModelsManager.excessLanguage(language: Language): Boolean {
  return modelsSchema.targetLanguage(language) != null && !MLServerCompletionSettings.getInstance().isEnabled(language)
}
