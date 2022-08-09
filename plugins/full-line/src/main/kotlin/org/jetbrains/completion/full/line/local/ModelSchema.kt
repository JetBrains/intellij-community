package org.jetbrains.completion.full.line.local

import com.fasterxml.jackson.annotation.JsonInclude
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlChildrenName
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlValue
import org.jetbrains.completion.full.line.models.CachingLocalPipeline
import org.jetbrains.completion.full.line.services.managers.LocalModelsManager
import java.io.File
import java.util.*

@Serializable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@SerialName("models")
data class LocalModelsSchema(
    @XmlElement(false)
    val version: Int?,
    @SerialName("model")
    val models: MutableList<ModelSchema>
) {
    // Getting model for current language with fix if (somehow) config has multiple models with same currentLanguage
    fun targetLanguage(id: String): ModelSchema? {
        val models = models.filter { it.currentLanguage == id }
        return when (models.size) {
            0    -> null
            1    -> models.first()
            else -> {
                models.maxByOrNull { it.version.split(".").last().toIntOrNull() ?: -1 }
            }
        }
    }

    fun targetLanguage(language: Language): ModelSchema? = targetLanguage(language.id.toLowerCase())
}

@Serializable
@SerialName("model")
data class ModelSchema(
    val version: String,
    val size: Long,

    @XmlElement(false)
    var currentLanguage: String?,
    @XmlChildrenName("language", "", "")
    val languages: List<String>,

    val binary: BinarySchema,
    val bpe: BPESchema,
    val config: ConfigSchema,

    val changelog: String,
) {
    private val root by lazy { LocalModelsManager.root.resolve(this.uid()) }

    fun uid() = UUID.nameUUIDFromBytes("${version}-${languages.joinToString()}".toByteArray()).toString()

    fun bpeFile() = root.resolve(bpe.path)
    fun binaryFile() = root.resolve(binary.path)
    fun configFile() = root.resolve(config.path)

    fun loadModel(loggingCallback: ((String) -> Unit)? = null): CachingLocalPipeline {
        assert(!ApplicationManager.getApplication().isDispatchThread) { "IO operations are prohibited in EDT" }
        return CachingLocalPipeline(
            bpeFile(), binaryFile(), configFile(),
            loggingCallback,
        )
    }

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

@Serializable
data class BPESchema(
    @XmlValue(true)
    val path: String,
)

@Serializable
data class ConfigSchema(
    @XmlValue(true)
    val path: String,
)

@Serializable
data class BinarySchema(
    @XmlValue(true)
    val path: String,
)
