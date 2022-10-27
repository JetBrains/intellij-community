package org.jetbrains.completion.full.line.services.managers

import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.io.ZipUtil
import org.jetbrains.completion.full.line.local.LocalModelsSchema
import org.jetbrains.completion.full.line.local.ModelSchema
import org.jetbrains.completion.full.line.local.decodeFromXml
import org.jetbrains.completion.full.line.local.encodeToXml
import org.jetbrains.completion.full.line.services.LocalModelsCache
import java.io.File
import java.nio.file.Files

class LocalModelsManager : ConfigurableModelsManager {

  private val modelsFile = root.resolve("models.xml")

  private val mavenManager = MavenModelsManager(root)

  override val modelsSchema: LocalModelsSchema

  init {
    modelsSchema = initSchema()
  }

  private fun initSchema(): LocalModelsSchema {
    return if (modelsFile.exists()) {
      modelsFile.readText().takeIf { it.isNotBlank() }?.let(::decodeFromXml)
      ?: LocalModelsSchema(1, mutableListOf()).also {
        modelsFile.createNewFile()
        modelsFile.writeText(encodeToXml(it))
      }
    }
    else {
      LocalModelsSchema(1, mutableListOf()).also {
        modelsFile.createNewFile()
        modelsFile.writeText(encodeToXml(it))
      }
    }
  }

  private fun removeCurrentSchema(language: Language): ModelSchema? {
    val schemaId = modelsSchema.models.indexOfFirst { it.currentLanguage == language.id.toLowerCase() }
    if (schemaId < 0) {
      return null
    }
    return modelsSchema.models.removeAt(schemaId)
  }

  override fun getLatest(language: Language, force: Boolean): ModelSchema {
    return mavenManager.getLatest(language, force)
  }

  override fun download(language: Language, force: Boolean): ModelSchema {
    return mavenManager.download(language, force).also {
      it.currentLanguage = language.id.toLowerCase()
      modelsSchema.models.add(it)
    }
  }

  override fun remove(language: Language) {
    removeCurrentSchema(language)?.also {
      root.resolve(it.uid()).deleteRecursively()
    }
  }

  override fun checkFile(language: Language): Boolean {
    val schema = modelsSchema.targetLanguage(language) ?: return false
    val uid = schema.uid()

    val dir = root.resolve(uid)
    return dir.exists()
           && dir.resolve(schema.bpe.path).exists()
           && dir.resolve(schema.config.path).exists()
           && dir.resolve(schema.binary.path).exists()
  }

  override fun update(language: Language, force: Boolean): ModelSchema {
    return mavenManager.update(language, force).also { newModel ->
      removeCurrentSchema(language)?.also {
        newModel.currentLanguage = it.currentLanguage
        modelsSchema.models.add(newModel)
      }
    }
  }

  override fun importLocal(language: Language, modelPath: String): ModelSchema {
    val modelFile = File(modelPath)
    val tmpFolder = root.resolve("tmp")

    return try {
      if (!modelFile.isDirectory) {
        ZipUtil.extract(modelFile.toPath(), tmpFolder.toPath(), null, true)
      }
      else {
        modelFile.copyRecursively(tmpFolder, true)
      }

      val model = ModelSchema.generateFromLocal(language, tmpFolder)

      root.resolve(model.uid()).let { destination ->
        if (!destination.exists()) {
          destination.mkdir()
        }
        listOf(model.binary.path, model.bpe.path, model.config.path).forEach {
          tmpFolder.resolve(it).renameTo(destination.resolve(it))
        }
      }

      removeCurrentSchema(language)?.also {
        modelsSchema.models.add(model)
      }

      model
    }
    finally {
      tmpFolder.deleteRecursively()
    }
  }


  override fun apply() {
    removeExcessModels(false)

    modelsSchema.also {
      modelsFile.writeText(encodeToXml(it))
    }

    LocalModelsCache.getInstance().invalidate()
  }

  override fun reset() {
    removeExcessModels(true)
    modelsSchema.models.clear()
    modelsSchema.models.addAll(decodeFromXml<LocalModelsSchema>(modelsFile.readText()).models)
  }

  /**
   * @param removeOutdated pass true to remove newly installed, or false for outdated models
   */
  private fun removeExcessModels(removeOutdated: Boolean) {
    val currentModels = modelsSchema.models
    val previousModels = decodeFromXml<LocalModelsSchema>(modelsFile.readText()).models

    if (removeOutdated) {
      currentModels - previousModels.toSet()
    }
    else {
      previousModels - currentModels.toSet()
    }.forEach {
      root.resolve(it.uid()).deleteRecursively()
    }
  }

  companion object {
    val LOG = thisLogger()
    val root by lazy {
      if (ApplicationManager.getApplication()?.isUnitTestMode == false) {
        File(PathManager.getSystemPath())
      }
      else {
        Files.createTempDirectory("full-line-temp").toFile()
      }.resolve("full-line/models")
        .also { Files.createDirectories(it.toPath()) }
    }
  }
}
