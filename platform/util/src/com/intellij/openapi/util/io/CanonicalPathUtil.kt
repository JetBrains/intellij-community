// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("MemberVisibilityCanBePrivate", "unused")
@file:JvmName("CanonicalPathUtil")

package com.intellij.openapi.util.io

import com.intellij.util.PathUtilRt
import com.intellij.util.containers.prefix.map.AbstractPrefixTreeFactory
import com.intellij.util.text.nullize
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths


fun String.toNioPath(): Path {
  return Paths.get(FileUtil.toSystemDependentName(this))
}

fun String.toIoFile(): File {
  return toNioPath().toFile()
}

fun String.getFileName(): String {
  return PathUtilRt.getFileName(this)
}

fun String.getParentPath(): String? {
  return PathUtilRt.getParentPath(this).nullize()
}

fun String.getParentNioPath(): Path? {
  return getParentPath()?.toNioPath()
}

fun String.getResolvedPath(relativePath: String): String {
  val path = "$this/$relativePath"
  return FileUtil.toCanonicalPath(path, '/') // resolve simple symlinks . and ..
}

fun String.getResolvedNioPath(relativePath: String): Path {
  return getResolvedPath(relativePath).toNioPath()
}

fun String.getRelativePath(path: String): String? {
  return FileUtil.getRelativePath(this, path, '/')
}

fun String.getRelativeNioPath(path: String): Path? {
  return getRelativePath(path)?.toNioPath()
}

fun String.isAncestor(path: String, strict: Boolean): Boolean {
  return FileUtil.isAncestor(this, path, strict)
}

object CanonicalPathPrefixTreeFactory : AbstractPrefixTreeFactory<String, String>() {

  override fun convertToList(element: String): List<String> {
    return element.removeSuffix("/").split("/")
  }
}
