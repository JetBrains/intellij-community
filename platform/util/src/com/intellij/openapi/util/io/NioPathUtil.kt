// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("NioPathUtil")

package com.intellij.openapi.util.io

import com.intellij.util.containers.prefix.map.AbstractPrefixTreeFactory
import java.io.IOException
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

fun Path.toCanonicalPath(): String {
  return FileUtil.toCanonicalPath(toString())
}

fun Path.getResolvedNioPath(relativePath: String): Path {
  return toCanonicalPath().getResolvedNioPath(relativePath)
}

fun Path.getRelativeNioPath(path: Path): Path? {
  return toCanonicalPath().getRelativeNioPath(path.toCanonicalPath())
}

fun Path.isAncestor(path: Path, strict: Boolean): Boolean {
  return FileUtil.isAncestor(this, path, strict)
}

fun Path.findOrCreateFile(): Path {
  parent?.createDirectories()
  if (!exists()) {
    createFile()
  }
  if (!isRegularFile()) {
    throw IOException("Expected file instead of directory: $this")
  }
  return this
}

fun Path.findOrCreateDirectory(): Path {
  createDirectories()
  if (!isDirectory()) {
    throw IOException("Expected directory instead of file: $this")
  }
  return this
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

fun Path.findOrCreateFile(relativePath: String): Path {
  return getResolvedNioPath(relativePath).findOrCreateFile()
}

fun Path.findOrCreateDirectory(relativePath: String): Path {
  return getResolvedNioPath(relativePath).findOrCreateDirectory()
}

fun Path.deleteRecursively(relativePath: String) {
  getResolvedNioPath(relativePath).deleteRecursively()
}

fun Path.deleteChildrenRecursively(relativePath: String, predicate: (Path) -> Boolean) {
  getResolvedNioPath(relativePath).deleteChildrenRecursively(predicate)
}

object NioPathPrefixTreeFactory : AbstractPrefixTreeFactory<Path, String>() {

  override fun convertToList(element: Path): List<String> {
    return CanonicalPathPrefixTreeFactory.convertToList(element.toCanonicalPath())
  }
}
