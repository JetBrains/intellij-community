package com.intellij.cce.actions

import com.google.gson.GsonBuilder
import com.intellij.cce.util.FilesHelper
import com.intellij.cce.workspace.storages.storage.ActionsSingleFileStorage
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.readText
import java.nio.file.Path
import kotlin.io.path.exists

class DatasetRefConverter {
  fun convert(datasetRef: DatasetRef, datasetContext: DatasetContext, project: Project): Path? {
    return when (datasetRef) {
      is AiPlatformFileRef -> {
        convert(datasetRef, datasetContext, project)
      }
      else -> null
    }
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
}