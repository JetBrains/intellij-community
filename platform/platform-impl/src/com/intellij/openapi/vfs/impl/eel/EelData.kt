// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.eel

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.fs.EelFileSystemApi.WatchedPath
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.utils.JEelUtils
import kotlin.io.path.Path

internal class EelData(val descriptor: EelDescriptor) {

  val recursive: MutableMap<String, String> = mutableMapOf()
  val flat: MutableMap<String, String> = mutableMapOf()

  @Volatile
  var ignored: List<String> = emptyList()

  fun reload(other: EelData) {
    recursive.clear()
    recursive.putAll(other.recursive)
    flat.clear()
    flat.putAll(other.flat)
    ignored = emptyList()
  }

  fun getWatchedPaths(): Set<WatchedPath> {
    return (recursive.values.mapNotNull {
      toContainerPath(it)?.let { eelPath -> WatchedPath.from(eelPath).recursive() }
    } + flat.values.mapNotNull {
      toContainerPath(it)?.let { eelPath -> WatchedPath.from(eelPath) }
    }).toSet()
  }

  fun toContainerPath(path: String): EelPath? =
    JEelUtils.toEelPath(kotlin.io.path.Path(path))

  internal fun findPath(path: String): String? {
    val originalRoot = recursive.keys.firstOrNull { path.startsWith(it) }?.let { recursive[it] }
             ?: flat.keys.firstOrNull { path.startsWith(it) }?.let { flat[it] } ?: return null
    return Path(originalRoot).resolve(Path(path)).toString()
  }
}