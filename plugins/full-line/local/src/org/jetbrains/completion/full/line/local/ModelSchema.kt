package org.jetbrains.completion.full.line.local

import com.intellij.lang.Language
import com.intellij.util.xmlb.annotations.*
import java.io.File
import java.util.*

@Tag("models")
data class LocalModelsSchema(
  @Attribute
  val version: Int?,
  @Property(surroundWithTag = false)
  @XCollection(propertyElementName = "model")
  val models: MutableList<ModelSchema>
) {
  @Suppress("unused")
  constructor() : this(null, mutableListOf())

  // Getting model for current language with fix if (somehow) config has multiple models with same currentLanguage
  fun targetLanguage(id: String): ModelSchema? {
    val models = models.filter { it.currentLanguage == id }
    return when (models.size) {
      0 -> null
      1 -> models.first()
      else -> {
        models.maxByOrNull { it.version.split(".").last().toIntOrNull() ?: -1 }
      }
    }
  }

  fun targetLanguage(language: Language): ModelSchema? = targetLanguage(language.id.toLowerCase())
}

@Tag("model")
data class ModelSchema(
  @Tag
  val version: String,
  @Tag
  val size: Long,

  @Attribute
  var currentLanguage: String?,
  @XCollection(propertyElementName = "languages", elementName = "language", valueAttributeName = "")
  val languages: List<String>,

  @Property(surroundWithTag = false)
  val binary: BinarySchema,
  @Property(surroundWithTag = false)
  val bpe: BPESchema,
  @Property(surroundWithTag = false)
  val config: ConfigSchema,
  @Tag
  val changelog: String,
) {
  @Suppress("unused")
  constructor() : this("", 1, "", emptyList(), BinarySchema(), BPESchema(), ConfigSchema(), "")

  fun uid() = UUID.nameUUIDFromBytes("${version}-${languages.joinToString()}".toByteArray()).toString()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ModelSchema

    if (uid() != other.uid()) return false

    return true
  }

  override fun hashCode(): Int {
    return uid().hashCode()
  }

  companion object {
    fun generateFromLocal(language: Language, file: File): ModelSchema {
      val content = file.listFiles() ?: throw LocalModelIsNotDirectory()
      val size = content.sumOf { it.length() }
      val lang = language.id.toLowerCase()

      val model = content.find { it.extension == "model" || it.extension == "onnx" || it.extension == "bin" }
                  ?: throw MissingPartOfLocalModel()
      val config = content.find { it.extension == "json" }
                   ?: throw MissingPartOfLocalModel()
      val bpe = content.find { it.extension == "bpe" }
                ?: throw MissingPartOfLocalModel()

      return ModelSchema(
        "SNAPSHOT-${System.currentTimeMillis()}",
        size,
        lang,
        listOf(lang),
        BinarySchema(model.name),
        BPESchema(bpe.name),
        ConfigSchema(config.name),
        ""
      )
    }
  }
}

@Tag("bpe")
data class BPESchema(
  @Text
  val path: String,
) {
  @Suppress("unused")
  constructor() : this("")
}

@Tag("config")
data class ConfigSchema(
  @Text
  val path: String,
) {
  @Suppress("unused")
  constructor() : this("")
}

@Tag("binary")
data class BinarySchema(
  @Text
  val path: String,
) {
  @Suppress("unused")
  constructor() : this("")
}
