// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.workspace.storages

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

interface StorageWithMetadata {
  val metadataFile: File
  fun saveMetadata()
}

abstract class StorageWithMetadataBase(val storageDir: String) : StorageWithMetadata {
  private var filesCounter: Int = 0

  protected val filesDir: Path = Paths.get(storageDir, "files")

  protected fun toFileName(filePath: String): String {
    return "${Paths.get(filePath).fileName}(${filesCounter++})"
  }

  override val metadataFile: File = Paths.get(storageDir, "files.json").toFile()
}
