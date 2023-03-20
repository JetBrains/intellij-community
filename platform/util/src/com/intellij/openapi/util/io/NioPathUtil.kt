// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("NioPathUtil")

package com.intellij.openapi.util.io

import com.intellij.util.containers.prefix.map.AbstractPrefixTreeFactory
import java.io.IOException
import java.nio.file.*
import kotlin.io.path.*

/**
 * Normalizes and returns path with forward slashes ('/').
 */
fun Path.toCanonicalPath(): String {
  return normalize().invariantSeparatorsPathString
}

/**
 * Resolves and normalizes path under [this] path.
 * I.e. resolve joins [this] and [relativePath] using file system separator
 */
fun Path.getResolvedPath(relativePath: String): Path {
  return resolve(FileUtil.toSystemDependentName(relativePath)).normalize()
}

/**
 * Checks that [this] is an ancestor of [path].
 *
 * @param strict if `false` then this method returns `true` if [this] equals to [file].
 * @return `true` if [this] is parent of [path]; `false` otherwise.
 */
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

fun Path.findOrCreateFile(relativePath: String): Path {
  return getResolvedPath(relativePath).findOrCreateFile()
}

fun Path.findOrCreateDirectory(relativePath: String): Path {
  return getResolvedPath(relativePath).findOrCreateDirectory()
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

object NioPathPrefixTreeFactory : AbstractPrefixTreeFactory<Path, String>() {

  override fun convertToList(element: Path): List<String> {
    return element.map { it.pathString }
  }
}

object CanonicalPathPrefixTreeFactory : AbstractPrefixTreeFactory<String, String>() {

  override fun convertToList(element: String): List<String> {
    return element.removeSuffix("/").split("/")
  }
}
