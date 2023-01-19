// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("NioPathUtil")

package com.intellij.openapi.util.io

import com.intellij.util.containers.prefix.map.AbstractPrefixTreeFactory
import java.io.IOException
import java.nio.file.*
import kotlin.io.path.*


/**
 * List of path directories and file name.
 *
 * For example, pathList for `"/a/b/c/d"` is `["a", "b", "c", "d"]`
 */
val Path.pathList: List<String>
  get() = map { it.pathString }

fun Path.toCanonicalPath(): String {
  return getResolvedPathString(null)
}

fun Path.getResolvedPath(): Path {
  return getResolvedPathString(null).toNioPath()
}

fun Path.getResolvedPath(relativePath: String): Path {
  return getResolvedPathString(relativePath).toNioPath()
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
  return getResolvedPath(relativePath).findOrCreateFile()
}

fun Path.findOrCreateDirectory(relativePath: String): Path {
  return getResolvedPath(relativePath).findOrCreateDirectory()
}

fun Path.deleteRecursively(relativePath: String) {
  getResolvedPath(relativePath).deleteRecursively()
}

fun Path.deleteChildrenRecursively(relativePath: String, predicate: (Path) -> Boolean) {
  getResolvedPath(relativePath).deleteChildrenRecursively(predicate)
}

fun String.toNioPath(): Path {
  return Paths.get(FileUtil.toSystemDependentName(this))
}

fun String.toNioPathOrNull(): Path? {
  return try {
    toNioPath()
  }
  catch (ex: InvalidPathException) {
    null
  }
}

fun String.getResolvedNioPath(relativePath: String): Path {
  return getResolvedPathString(relativePath).toNioPath()
}

private fun Path.getResolvedPathString(relativePath: String?): String {
  return invariantSeparatorsPathString.getResolvedPathString(relativePath)
}

private fun String.getResolvedPathString(relativePath: String?): String {
  val path = when (relativePath) {
    null -> this
    else -> "$this/$relativePath"
  }
  return FileUtil.toCanonicalPath(path, '/', true)
}

object NioPathPrefixTreeFactory : AbstractPrefixTreeFactory<Path, String>() {

  override fun convertToList(element: Path): List<String> {
    return element.pathList
  }
}

object CanonicalPathPrefixTreeFactory : AbstractPrefixTreeFactory<String, String>() {

  override fun convertToList(element: String): List<String> {
    return element.removeSuffix("/").split("/")
  }
}
