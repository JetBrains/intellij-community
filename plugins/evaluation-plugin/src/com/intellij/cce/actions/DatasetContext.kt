package com.intellij.cce.actions

import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.cce.workspace.storages.FileErrorsStorage
import com.intellij.cce.workspace.storages.storage.ActionsStorage
import com.intellij.cce.workspace.storages.storage.ActionsStorageFactory
import com.intellij.cce.workspace.storages.storage.getActionsStorageTypeFromEnv
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Provides a context for working with datasets.
 * It is supposed to have everything needed to generate and store data.
 */
class DatasetContext(
  private val outputWorkspace: EvaluationWorkspace,
  private val actionWorkspace: EvaluationWorkspace?,
  internal val configPath: String?,
) {
  private val datasetDir = Paths.get("bin/ml-eval-datasets")

  /*
    EvaluationDataset should be extracted into 3 parts - steps, dataset creation and dataset.
    this will become simpler after refactoring
   */
  private var _actionsStorage: ActionsStorage? = null
  val actionsStorage: ActionsStorage
    get(): ActionsStorage {
      if (_actionsStorage == null) {
        check(actionWorkspace != null) { "It seems that workspace with actions wasn't configured" }

        val directory = actionWorkspace.path().resolve("actions")
        Files.createDirectories(directory)
        _actionsStorage = ActionsStorageFactory.create(directory.toString(), getActionsStorageTypeFromEnv())
      }
      return _actionsStorage!!
    }

  fun replaceActionsStorage(storage: ActionsStorage) {
    _actionsStorage = storage
  }

  val errorsStorage: FileErrorsStorage = outputWorkspace.errorsStorage

  fun saveAdditionalStats(name: String, stats: Map<String, Any>) {
    outputWorkspace.saveAdditionalStats(name, stats)
  }

  fun path(name: String): Path {
    Files.createDirectories(datasetDir)
    return datasetDir.resolve(name)
  }

  fun path(ref: DatasetRef): Path = path(ref.name)
}