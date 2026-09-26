// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginSystem.testFramework

import com.intellij.platform.pluginSystem.parser.impl.LoadedXIncludeReference
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString
import kotlin.io.path.readBytes

fun isLibraryXiIncludeTarget(path: String, targets: Iterable<String>): Boolean {
  val normalizedPath = path.removePrefix("/")
  return targets.any { target ->
    val normalizedTarget = target.removePrefix("/")
    if (normalizedTarget.endsWith("*")) {
      normalizedPath.startsWith(normalizedTarget.removeSuffix("*"))
    }
    else {
      normalizedPath == normalizedTarget
    }
  }
}

/**
 * Resolves [path] through packaged filesystem roots. A root is either a directory or a jar path resolved by packaging metadata.
 */
fun loadXIncludeReferenceFromResolvedRoots(path: String, roots: Sequence<Path>): LoadedXIncludeReference? {
  val normalizedPath = path.removePrefix("/")
  for (root in roots) {
    loadXIncludeReferenceFromLibraryRoot(normalizedPath, root)?.let {
      return it
    }
  }
  return null
}

private fun loadXIncludeReferenceFromLibraryRoot(path: String, root: Path): LoadedXIncludeReference? {
  if (root.isDirectory()) {
    val file = root.resolve(path)
    if (Files.isRegularFile(file)) {
      return LoadedXIncludeReference(file.readBytes(), file.pathString)
    }
    return null
  }
  if (!Files.isRegularFile(root)) {
    return null
  }
  FileSystems.newFileSystem(root).use { zipFileSystem ->
    val file = zipFileSystem.getPath(path)
    if (!Files.isRegularFile(file)) {
      return null
    }
    return LoadedXIncludeReference(file.readBytes(), "${root.pathString}!/$path")
  }
}
