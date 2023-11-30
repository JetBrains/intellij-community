// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils.io

import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.io.findOrCreateDirectory
import com.intellij.openapi.util.io.findOrCreateFile
import com.intellij.openapi.util.io.getResolvedPath
import java.io.IOException
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries

@Deprecated(
  message = "Use stdlib function",
  replaceWith = ReplaceWith("listDirectoryEntries()", "kotlin.io.path.listDirectoryEntries"),
  level = DeprecationLevel.ERROR,
)
fun Path.getChildren(): List<Path> = listDirectoryEntries()

fun Path.createFile(): Path {
  if (exists()) {
    throw IOException("File already exists: $this")
  }
  return findOrCreateFile()
}

fun Path.createDirectory(): Path {
  if (exists()) {
    throw IOException("Directory already exists: $this")
  }
  return findOrCreateDirectory()
}

fun Path.createFile(relativePath: String): Path {
  return getResolvedPath(relativePath).createFile()
}

fun Path.createDirectory(relativePath: String): Path {
  return getResolvedPath(relativePath).createDirectory()
}

fun Path.deleteRecursively() {
  NioFiles.deleteRecursively(this)
}

fun Path.deleteChildrenRecursively(predicate: (Path) -> Boolean) {
  val filter = DirectoryStream.Filter(predicate)
  Files.newDirectoryStream(this, filter).use { stream ->
    stream.forEach { it.deleteRecursively() }
  }
}

fun Path.deleteRecursively(relativePath: String) {
  getResolvedPath(relativePath).deleteRecursively()
}

fun Path.deleteChildrenRecursively(relativePath: String, predicate: (Path) -> Boolean) {
  getResolvedPath(relativePath).deleteChildrenRecursively(predicate)
}
