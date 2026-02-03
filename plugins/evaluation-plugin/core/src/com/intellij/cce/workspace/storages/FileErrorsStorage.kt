// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.workspace.storages

import com.intellij.cce.workspace.info.FileErrorInfo
import com.intellij.cce.workspace.info.FileErrorSerializer
import java.nio.file.Paths

class FileErrorsStorage(storageDir: String) {
  companion object {
    private val fileErrorSerializer = FileErrorSerializer()
  }

  private val keyValueStorage = FileArchivesStorage(storageDir)
  private var filesCounter = 0

  fun saveError(error: FileErrorInfo) {
    filesCounter++
    val json = fileErrorSerializer.serialize(error)
    keyValueStorage.save("${Paths.get(error.path).fileName}($filesCounter).json", json)
  }

  fun getErrors(): List<FileErrorInfo> {
    return keyValueStorage.getKeys().map { fileErrorSerializer.deserialize(keyValueStorage.get(it)) }
  }
}