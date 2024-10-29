// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.workspace.storages.storage

import com.intellij.cce.actions.FileActions
import com.intellij.cce.workspace.storages.ensureDirExists
import java.nio.file.Paths

interface ActionsStorage {
  fun saveActions(actions: FileActions)
  fun computeSessionsCount(): Int
  fun getActionFiles(): List<String>
  fun getActions(path: String): FileActions
}

enum class ActionsStorageType {
  MULTIPLY_FILES,
  SINGLE_FILE,
}

fun getActionsStorageTypeFromEnv(): ActionsStorageType {
  val envVar = System.getenv("AIA_EVALUATION_ACTIONS_STORAGE_TYPE")?.lowercase() ?: ""
  return ActionsStorageType.entries.firstOrNull { it.name.lowercase() == envVar } ?: ActionsStorageType.MULTIPLY_FILES
}

object ActionsStorageFactory {
  fun create(storageDir: String, type: ActionsStorageType): ActionsStorage {
    ensureDirExists(storageDir)

    return when (type) {
      ActionsStorageType.MULTIPLY_FILES -> ActionsMultiplyFilesStorage(storageDir)
      ActionsStorageType.SINGLE_FILE -> ActionsSingleFileStorage(Paths.get(storageDir, "actions"))
    }
  }
}