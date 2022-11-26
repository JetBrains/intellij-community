package org.jetbrains.completion.full.line.local.loader

import org.jetbrains.completion.full.line.local.loader.CompletionModelLoader.FromFile
import org.jetbrains.completion.full.line.local.loader.CompletionModelLoader.FromGetter
import org.jetbrains.completion.full.line.local.utils.JSON
import java.io.File

/**
 * Interface defining loader of completion model.
 *
 * Completion model requires model in ONNX format and its configuration, as well as vocabulary and BPE merges files for tokenizer.
 *
 * User can load it from custom location via [FromGetter] or from local files via [FromFile]
 */
sealed class CompletionModelLoader {
  companion object {
    fun deserializeConfig(text: String): Map<String, Int> {
      return JSON.parse(text)
    }

    fun deserializeVocabulary(text: String): Map<String, Int> {
      return JSON.parse(text)
    }

    fun deserializeMerges(text: String): List<Pair<String, String>> {
      return text.lines().filterNot { it.startsWith("#") || it.isBlank() }.map { it.split(" ") }.map { (left, right) -> left to right }
    }
  }

  abstract fun getModel(): ByteArray
  abstract fun getMerges(): List<Pair<String, String>>
  abstract fun getVocabulary(): Map<String, Int>
  abstract fun getConfig(): Map<String, Int>

  /** Load Completion model from local files */
  class FromFile(model: File, vocabulary: File, merges: File, config: File) :
    FromGetter({ model.readBytes() }, { vocabulary.readText() }, { merges.readText() }, { config.readText() })

  /** Load Completion via custom access methods */
  open class FromGetter(val model: () -> ByteArray, val vocabulary: () -> String, val merges: () -> String, val config: () -> String) :
    CompletionModelLoader() {
    override fun getModel(): ByteArray = model()
    override fun getVocabulary() = deserializeVocabulary(vocabulary())
    override fun getMerges() = deserializeMerges(merges())
    override fun getConfig() = deserializeConfig(config())
  }
}
