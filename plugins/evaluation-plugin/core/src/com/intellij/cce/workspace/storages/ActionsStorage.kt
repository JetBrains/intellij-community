// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.workspace.storages

import com.intellij.cce.actions.ActionSerializer
import com.intellij.cce.actions.FileActions
import java.nio.file.Paths

class ActionsStorage(storageDir: String) {
  private val keyValueStorage = FileArchivesStorage(storageDir)
  private var filesCounter = 0

  fun saveActions(actions: FileActions) {
    filesCounter++
    keyValueStorage.save("${Paths.get(actions.path).fileName}($filesCounter).json", ActionSerializer.serialize(actions))
  }

  fun computeSessionsCount(): Int {
    var count = 0
    for (file in getActionFiles())
      count += ActionSerializer.getSessionsCount(keyValueStorage.get(file))
    return count
  }

  fun getActionFiles(): List<String> = keyValueStorage.getKeys().sortedBy {
    it.substringAfterLast('(').substringBefore(')').toInt()
  }

  fun getActions(path: String): FileActions {
    return ActionSerializer.deserialize(keyValueStorage.get(path))
  }
}