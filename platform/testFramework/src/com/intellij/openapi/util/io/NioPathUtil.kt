// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists


val Path.children: List<Path>
  get() = Files.newDirectoryStream(this).use { it.toList() }

fun Path.createFile(): Path {
  check(!exists()) {
    "File already exists: $this"
  }
  return findOrCreateFile()
}

fun Path.createDirectory(): Path {
  check(!exists()) {
    "Directory already exists: $this"
  }
  return findOrCreateDirectory()
}

fun Path.createFile(relativePath: String): Path {
  return getResolvedPath(relativePath).createFile()
}

fun Path.createDirectory(relativePath: String): Path {
  return getResolvedPath(relativePath).createDirectory()
}