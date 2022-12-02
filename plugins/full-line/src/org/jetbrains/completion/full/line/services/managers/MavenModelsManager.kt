package org.jetbrains.completion.full.line.services.managers

import com.intellij.lang.Language
import com.intellij.openapi.components.service
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.download.DownloadableFileService
import com.intellij.util.io.HttpRequests
import org.jetbrains.completion.full.line.language.FullLineLanguageSupporter
import org.jetbrains.completion.full.line.local.MavenMetadata
import org.jetbrains.completion.full.line.local.ModelSchema
import org.jetbrains.completion.full.line.local.decodeFromXml
import java.io.File

class MavenModelsManager(private val root: File) : ModelsManager {
  private val cached: HashMap<String, ModelSchema> = HashMap()

  override fun getLatest(language: Language, force: Boolean): ModelSchema {
    return if (force || !cached.containsKey(language.id)) {
      val metadata = HttpRequests.request("${mavenHost(language)}/maven-metadata.xml").connect { r ->
        val content = r.reader.readText()
        decodeFromXml<MavenMetadata>(content)
      }

      val latest = if (Registry.`is`("full.line.local.models.beta")) {
        metadata.versioning.latest
      }
      else {
        metadata.versioning.release
      }

      modelById(language, latest)
    }
    else {
      cached.getValue(language.id)
    }
  }

  private fun modelById(language: Language, version: String): ModelSchema {
    return HttpRequests.request("${mavenHost(language)}/$version/model.xml").connect { r ->
      val content = r.reader.readText()
      decodeFromXml<ModelSchema>(content)
    }.also { cached[language.id] = it }
  }

  override fun download(language: Language, force: Boolean): ModelSchema {
    val supporter = FullLineLanguageSupporter.getInstance(language)
    val model = if (force || !cached.containsKey(language.id)) {
      supporter?.modelVersion?.let { modelById(language, it) } ?: getLatest(language, force)
    }
    else {
      cached.getValue(language.id)
    }
    val downloadableService = service<DownloadableFileService>()

    downloadableService.createDownloader(
      listOf(model.binary.path, model.bpe.path, model.config.path).map {
        downloadableService.createFileDescription("${mavenHost(language)}/${model.version}/${it}", it)
      }, "${language.displayName} model"
    ).download(root.resolve(model.uid()))

    return model
  }

  override fun update(language: Language, force: Boolean) = download(language, force)

  private fun mavenHost(language: Language): String {
    var langId = language.id.lowercase()
    if (langId == "python") langId = "python-v2" // TODO: temp fix for new models

    return "https://packages.jetbrains.team/maven/p/ccrm/flcc-local-models" +
           "/org/jetbrains/completion/full/line/local-model-$langId"
  }
}
