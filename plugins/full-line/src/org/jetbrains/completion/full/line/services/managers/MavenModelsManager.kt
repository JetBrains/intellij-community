package org.jetbrains.completion.full.line.services.managers

import com.intellij.lang.Language
import com.intellij.openapi.components.service
import com.intellij.util.download.DownloadableFileService
import com.intellij.util.io.HttpRequests
import org.jetbrains.completion.full.line.language.FullLineLanguageSupporter
import org.jetbrains.completion.full.line.local.ModelSchema
import org.jetbrains.completion.full.line.local.decodeFromXml
import java.io.File

class MavenModelsManager(private val root: File) : ModelsManager {
  private val cached: HashMap<String, ModelSchema> = HashMap()

  override fun getSchema(language: Language, force: Boolean): ModelSchema {
    return if (force || !cached.containsKey(language.id)) {
      modelWithId(language)
    }
    else {
      cached.getValue(language.id)
    }
  }

  private fun modelWithId(language: Language): ModelSchema {
    val supporter = FullLineLanguageSupporter.getInstance(language)!!

    return HttpRequests.request("${mavenHost(language)}/${supporter.modelVersion}/model.xml").connect { r ->
      val content = r.reader.readText()
      decodeFromXml<ModelSchema>(content)
    }.also { cached[language.id] = it }
  }

  override fun download(language: Language, force: Boolean): ModelSchema {
    val model = getSchema(language, force)
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
