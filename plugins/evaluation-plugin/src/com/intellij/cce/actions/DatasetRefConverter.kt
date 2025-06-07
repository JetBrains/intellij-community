package com.intellij.cce.actions

import com.google.gson.GsonBuilder
import com.intellij.cce.actions.simplified.DatasetTransformer
import com.intellij.cce.actions.simplified.FileOffsetProvider
import com.intellij.cce.actions.simplified.SimplifiedDatasetSerializer
import com.intellij.cce.util.FilesHelper
import com.intellij.cce.workspace.storages.storage.ActionsSingleFileStorage
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.readText
import com.intellij.util.io.write
import java.nio.file.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

class DatasetRefConverter {
  fun convert(datasetRef: DatasetRef, datasetContext: DatasetContext, project: Project): Path? {
    if (datasetRef is AiPlatformFileRef) {
      return convert(datasetRef, datasetContext, project)
    }

    val path = datasetContext.path(datasetRef)
    val preview = readHead(path, charLimit = 1000)

    if (preview?.contains("\"openFiles\": [") == true) { // looks like simplified action format
      return convertSimplified(path, datasetContext, project)
    }

    return null
  }

  private fun convertSimplified(simplifiedActionsPath: Path, datasetContext: DatasetContext, project: Project): Path {
    val convertedPath = datasetContext.path("actions_converted_from_simplified_${project.name}.json")
    val dataset = SimplifiedDatasetSerializer.parseJson(simplifiedActionsPath.readText()).toList()
    val transformedDataset = DatasetTransformer(FileOffsetProvider(project.basePath!!)).transform(dataset)
    val resultContent = ActionArraySerializer.serialize(transformedDataset.toTypedArray())
    convertedPath.write(resultContent)
    return convertedPath
  }

  private fun convert(datasetRef: AiPlatformFileRef, datasetContext: DatasetContext, project: Project): Path {
    val originalName = datasetRef.name
    val parts = originalName.split("?")
    if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
      throw IllegalArgumentException("Dataset name should be in format <file_name>?<repo_name>")
    }
    val file = parts[0]
    val repo = parts[1]

    val repoSuffix = repo.replace("/", "_")
    val filteredFile = "${file}_${repoSuffix}_filtered.jsonl"
    val convertedFile = "${file}_${repoSuffix}_converted.jsonl"

    val path = datasetContext.path(file)

    check(path.exists()) {
      "Dataset $file does not exist: $path"
    }

    val text = path.toFile().readText()
    val parsedItems = AiPlatformDatasetParser().parse(text)
    val filteredByRepo = parsedItems.filter { it.metadata.source.endsWith(repo) }

    val fileWithFilteredItems = datasetContext.path(filteredFile).toFile()
    if (fileWithFilteredItems.exists()) fileWithFilteredItems.delete()
    val gson = GsonBuilder().setPrettyPrinting().create()
    fileWithFilteredItems.writeText(gson.toJson(filteredByRepo))

    val fileContentProvider = object : FileContentProvider {
      override fun getContent(path: String): String {
        return FilesHelper.getFile(project, path).readText()
      }
    }

    val actions = AiPlatformDatasetConverter(fileContentProvider).convert(filteredByRepo)
    val actionsPath = datasetContext.path(convertedFile)
    actionsPath.toFile().run {
      if (exists()) delete()
    }
    val newStorage = ActionsSingleFileStorage(actionsPath)
    actions.forEach {
      newStorage.saveActions(it)
    }

    return actionsPath
  }

  private fun readHead(path: Path, charLimit: Int): String? {
    if (!path.isRegularFile()) {
      return null
    }

    return path.bufferedReader().use {
      val buffer = CharArray(charLimit)
      val charsRead = it.read(buffer, 0, charLimit)
      if (charsRead > 0) String(buffer, 0, charsRead) else null
    }
  }
}