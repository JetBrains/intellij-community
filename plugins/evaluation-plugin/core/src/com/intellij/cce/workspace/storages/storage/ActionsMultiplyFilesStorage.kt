// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.workspace.storages.storage

import com.intellij.cce.actions.ActionSerializer
import com.intellij.cce.actions.FileActions
import com.intellij.cce.workspace.storages.FileArchivesStorage
import java.nio.file.Paths

internal class ActionsMultiplyFilesStorage(storageDir: String) : ActionsStorage {
  private val keyValueStorage = FileArchivesStorage(storageDir)
  private var filesCounter = 0

  override fun saveActions(actions: FileActions) {
    filesCounter++
    keyValueStorage.save("${Paths.get(actions.path).fileName}($filesCounter).json", ActionSerializer.serializeFileActions(actions))
  }

  override fun computeSessionsCount(): Int {
    var count = 0
    for (file in getActionFiles())
      count += ActionSerializer.getSessionsCount(keyValueStorage.get(file))
    return count
  }

  override fun getActionFiles(): List<String> = keyValueStorage.getKeys().sortedBy {
    it.substringAfterLast('(').substringBefore(')').toInt()
  }

  override fun getActions(path: String): FileActions {
    return ActionSerializer.deserializeFileActions(keyValueStorage.get(path))
  }
}